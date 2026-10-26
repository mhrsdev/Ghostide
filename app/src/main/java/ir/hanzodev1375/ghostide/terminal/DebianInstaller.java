package ir.hanzodev1375.ghostide.terminal;

import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import ir.hanzodev1375.ghostide.R;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Process-wide, single-flight Debian rootfs installer (resolve, download, extract).
 *
 * <p>The install is owned by this class, never by an Activity or Fragment: UIs only {@link
 * #attach(InstallListener)} and {@link #detach(InstallListener)} a listener. Only the application
 * context is retained. All public methods must be called on the main thread and every {@link
 * InstallListener} callback is delivered on the main thread. Download progress is coalesced to
 * percent changes, so a UI receives at most ~100 download updates per install.
 */
public final class DebianInstaller {

  private DebianInstaller() {}

  private static final String LOG_TAG = "GhostDebianInstaller";
  private static final String IMAGES_BASE =
      "https://images.linuxcontainers.org/images/debian/bookworm/";
  private static final String ROOTFS_FILE_NAME = "debian-rootfs.tar.xz";
  private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("(\\d{8}_\\d{2}:\\d{2})/");
  private static final ExecutorService resolveExecutor = Executors.newSingleThreadExecutor();
  private static final OkHttpClient http = new OkHttpClient();
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());

  /** Callbacks of the running install; all of them are invoked on the main thread. */
  public interface InstallListener {
    void onDownloadProgress(int percent);

    void onExtractProgress(int extractedEntries);

    void onSuccess();

    void onError(String message);
  }

  /** Current install phase; process-wide and independent of any Activity or Fragment. */
  public enum Phase {
    IDLE,
    DOWNLOADING,
    EXTRACTING,
    DONE,
    ERROR
  }

  private static volatile Phase phase = Phase.IDLE;
  private static int progressValue = -1;
  private static InstallListener uiListener;
  private static int activeDownloadId = -1;
  private static int generation;
  private static CancellationSignal extractSignal;
  private static boolean cancellingExtraction;
  private static boolean restartAfterCancel;
  private static String pendingUrl;
  private static Context appContext;

  /** True while a download or an extraction that has not been cancelled runs. */
  public static boolean isInstalling() {
    return phase == Phase.DOWNLOADING || phase == Phase.EXTRACTING;
  }

  public static Phase getPhase() {
    return phase;
  }

  /**
   * Attaches a UI to the install without restarting it and immediately replays the last known
   * progress. When no install is running the listener is only kept for a later one.
   */
  @MainThread
  public static void attach(InstallListener listener) {
    uiListener = listener;
    if (listener == null || progressValue < 0) return;
    if (phase == Phase.DOWNLOADING) listener.onDownloadProgress(progressValue);
    else if (phase == Phase.EXTRACTING) listener.onExtractProgress(progressValue);
  }

  /** Detaches {@code listener} only if it is still the attached one. */
  @MainThread
  public static void detach(InstallListener listener) {
    if (uiListener == listener) uiListener = null;
  }

  public static boolean isInstalled(Context context) {
    return DebianBootstrap.isInstalled(context);
  }

  private static String detectArch() {
    for (String abi : Build.SUPPORTED_ABIS) {
      switch (abi) {
        case "arm64-v8a":
          return "arm64";
        case "armeabi-v7a":
          return "armhf";
        case "x86_64":
          return "amd64";
        case "x86":
          return "i386";
      }
    }
    return "arm64";
  }

  /**
   * Starts resolving, downloading and extracting the latest Debian rootfs. If an install is already
   * running, {@code listener} is attached to it instead of starting a second one.
   */
  @MainThread
  public static void installDebian(Context context, InstallListener listener) {
    start(context, null, listener);
  }

  /** Like {@link #installDebian} but downloads the rootfs archive from {@code url}. */
  @MainThread
  public static void installFrom(Context context, String url, InstallListener listener) {
    start(context, url, listener);
  }

  private static void start(Context context, String url, InstallListener listener) {
    appContext = context.getApplicationContext();
    if (cancellingExtraction) {
      restartAfterCancel = true;
      pendingUrl = url;
      uiListener = listener;
      return;
    }
    if (isInstalling()) {
      attach(listener);
      return;
    }
    uiListener = listener;
    phase = Phase.DOWNLOADING;
    progressValue = -1;
    int gen = ++generation;
    if (url != null) {
      startDownloadAndExtract(gen, url);
      return;
    }
    Context app = appContext;
    String arch = detectArch();
    resolveExecutor.execute(
        () -> {
          try {
            String resolved = resolveLatestRootfsUrl(app, arch);
            mainHandler.post(
                () -> {
                  if (gen == generation) startDownloadAndExtract(gen, resolved);
                });
          } catch (IOException e) {
            String message =
                app.getString(R.string.terminal_error_resolve_rootfs_failed, arch, e.getMessage());
            mainHandler.post(() -> fail(gen, message));
          }
        });
  }

  private static String resolveLatestRootfsUrl(Context context, String arch) throws IOException {
    String indexUrl = IMAGES_BASE + arch + "/default/";
    Request request = new Request.Builder().url(indexUrl).build();
    String latestSnapshot = null;
    try (Response response = http.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new IOException(
            context.getString(R.string.terminal_error_http_status, response.code(), indexUrl));
      }
      String html = response.body().string();
      Matcher matcher = SNAPSHOT_PATTERN.matcher(html);
      while (matcher.find()) {
        String snapshot = matcher.group(1);
        if (latestSnapshot == null || snapshot.compareTo(latestSnapshot) > 0) {
          latestSnapshot = snapshot;
        }
      }
    }
    if (latestSnapshot == null) {
      throw new IOException(context.getString(R.string.terminal_error_no_snapshot_found, indexUrl));
    }
    return indexUrl + latestSnapshot.replace(":", "%3A") + "/rootfs.tar.xz";
  }

  private static void startDownloadAndExtract(int gen, String url) {
    Context app = appContext;
    File downloadDir = app.getCacheDir();
    File downloadedFile = new File(downloadDir, ROOTFS_FILE_NAME);

    activeDownloadId =
        PRDownloader.download(url, downloadDir.getAbsolutePath(), ROOTFS_FILE_NAME)
            .build()
            .setOnProgressListener(
                progress -> {
                  if (gen != generation || progress.totalBytes <= 0) return;
                  int percent = (int) (progress.currentBytes * 100 / progress.totalBytes);
                  if (percent == progressValue) return;
                  progressValue = percent;
                  if (uiListener != null) uiListener.onDownloadProgress(percent);
                })
            .start(
                new OnDownloadListener() {
                  @Override
                  public void onDownloadComplete() {
                    if (gen != generation) return;
                    activeDownloadId = -1;
                    startExtraction(gen, downloadedFile);
                  }

                  @Override
                  public void onError(Error error) {
                    if (gen != generation) return;
                    activeDownloadId = -1;
                    fail(gen, app.getString(R.string.terminal_error_download_failed_prefix,
                        describe(app, error)));
                  }
                });
  }

  private static String describe(Context context, Error error) {
    if (error == null) return context.getString(R.string.terminal_unknown_error);
    String server = error.getServerErrorMessage();
    if (server != null && !server.isEmpty()) return server;
    Throwable connection = error.getConnectionException();
    if (connection != null && connection.getMessage() != null) return connection.getMessage();
    return context.getString(R.string.terminal_unknown_error);
  }

  private static void startExtraction(int gen, File downloadedFile) {
    phase = Phase.EXTRACTING;
    progressValue = -1;
    CancellationSignal signal = new CancellationSignal();
    extractSignal = signal;
    Context app = appContext;
    DebianBootstrap.installFromTarXz(
        app,
        downloadedFile,
        signal,
        new DebianBootstrap.InstallCallback() {
          @Override
          public void onProgress(int extractedEntries) {
            if (gen != generation || extractedEntries == progressValue) return;
            progressValue = extractedEntries;
            if (uiListener != null) uiListener.onExtractProgress(extractedEntries);
          }

          @Override
          public void onSuccess() {
            if (gen != generation) return;
            extractSignal = null;
            phase = Phase.DONE;
            if (uiListener != null) uiListener.onSuccess();
          }

          @Override
          public void onError(Exception e) {
            if (gen != generation) return;
            extractSignal = null;
            fail(gen, app.getString(R.string.terminal_error_extract_failed_prefix, e.getMessage()));
          }

          @Override
          public void onCancelled() {
            onExtractionCancelled(signal);
          }
        });
  }

  private static void onExtractionCancelled(CancellationSignal signal) {
    if (extractSignal != signal) return;
    extractSignal = null;
    cancellingExtraction = false;
    if (restartAfterCancel && appContext != null) {
      restartAfterCancel = false;
      String url = pendingUrl;
      pendingUrl = null;
      start(appContext, url, uiListener);
    }
  }

  private static void fail(int gen, String message) {
    if (gen != generation) return;
    phase = Phase.ERROR;
    Log.w(LOG_TAG, message);
    if (uiListener != null) uiListener.onError(message);
  }

  /**
   * Cancels the running install; no further callback of it is delivered. A download stops
   * immediately. An extraction is stopped and its partial rootfs is removed in the background; an
   * install requested before that cleanup finishes starts right after it instead of racing it.
   */
  @MainThread
  public static void cancelInstall() {
    if (phase == Phase.DOWNLOADING) {
      generation++;
      if (activeDownloadId != -1) {
        PRDownloader.cancel(activeDownloadId);
        activeDownloadId = -1;
      }
      phase = Phase.IDLE;
    } else if (phase == Phase.EXTRACTING && !cancellingExtraction && extractSignal != null) {
      generation++;
      cancellingExtraction = true;
      restartAfterCancel = false;
      phase = Phase.IDLE;
      extractSignal.cancel();
    }
  }
}

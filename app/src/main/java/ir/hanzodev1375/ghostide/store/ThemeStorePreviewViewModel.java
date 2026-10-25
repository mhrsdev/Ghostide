package ir.hanzodev1375.ghostide.store;

import android.app.Application;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.blankj.utilcode.util.FileIOUtils;
import com.blankj.utilcode.util.FileUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ir.hanzodev1375.components.store.api.ThemesApi;
import ir.hanzodev1375.components.store.event.ThemeInstalledEvent;
import ir.hanzodev1375.components.store.model.ThemeItem;
import ir.theme.GhostTheme;
import ir.theme.ThemeManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.greenrobot.eventbus.EventBus;

/**
 * Downloads, parses and caches store theme previews for {@link ThemeStorePreviewSheet}.
 *
 * <p>Scoped to the sheet, so it survives rotation and is cleared when the sheet is dismissed. All
 * network and file work runs on one background thread; parsed previews are kept in a bounded
 * {@link LruCache} so stepping back and forth between themes rebinds instantly. Results for a theme
 * that is no longer selected are cached but never published, so out-of-order completions cannot
 * show the wrong theme.
 */
public class ThemeStorePreviewViewModel extends AndroidViewModel {

  public static final int STATE_IDLE = 0;
  public static final int STATE_DOWNLOADING = 1;
  public static final int STATE_READY = 2;
  public static final int STATE_ERROR = 3;

  private static final String TAG = "ThemeStorePreview";
  private static final String PREVIEW_DIR = "theme_preview";
  private static final String THEMES_DIR = "ghostide/themes";
  private static final String THEME_EXTENSION = ".gth";
  private static final int CACHE_BYTES = 12 * 1024 * 1024;
  private static final int DESCRIPTION_CACHE_ENTRIES = 16;
  private static final int BUFFER_SIZE = 8192;
  private static final int PERCENT = 100;

  private static final OkHttpClient client =
      new OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .build();

  /** What the sheet shows for the selected theme. */
  public static final class PreviewState {
    public final String key;
    public final int state;
    public final int progress;
    @Nullable final ThemePreviewData data;

    PreviewState(String key, int state, int progress, @Nullable ThemePreviewData data) {
      this.key = key;
      this.state = state;
      this.progress = progress;
      this.data = data;
    }
  }

  /** Outcome of {@link #install}: {@code success} or not, for the theme named {@code name}. */
  public static final class InstallResult {
    public final boolean success;
    public final String name;

    InstallResult(boolean success, String name) {
      this.success = success;
      this.name = name;
    }
  }

  private final MutableLiveData<PreviewState> preview = new MutableLiveData<>();
  private final MutableLiveData<String> description = new MutableLiveData<>();
  private final MutableLiveData<InstallResult> installResult = new MutableLiveData<>();
  private final MutableLiveData<Boolean> installing = new MutableLiveData<>(false);
  private final LruCache<String, ThemePreviewData> cache =
      new LruCache<String, ThemePreviewData>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, ThemePreviewData value) {
          return value.byteCount();
        }
      };
  private final LruCache<String, String> descriptions = new LruCache<>(DESCRIPTION_CACHE_ENTRIES);
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  /** Key of the theme the sheet currently shows; written on the main thread only. */
  @Nullable private volatile String selectedKey;

  @Nullable private String selectedDescriptionUrl;
  private volatile boolean cleared;

  public ThemeStorePreviewViewModel(@NonNull Application application) {
    super(application);
  }

  public LiveData<PreviewState> getPreview() {
    return preview;
  }

  public LiveData<String> getDescription() {
    return description;
  }

  public LiveData<InstallResult> getInstallResult() {
    return installResult;
  }

  public LiveData<Boolean> getInstalling() {
    return installing;
  }

  /** Stable cache key of a store entry. */
  static String keyOf(ThemeItem item) {
    String link = item.linkdownload();
    return link != null && !link.isEmpty() ? link : String.valueOf(item.name());
  }

  /**
   * Makes {@code item} the shown theme: publishes the cached preview at once, or downloads and
   * parses it on the background thread.
   *
   * @param previewWidthPx width the preview is drawn at, used to downscale the wallpaper.
   */
  @MainThread
  public void select(ThemeItem item, int previewWidthPx) {
    String key = keyOf(item);
    PreviewState current = preview.getValue();
    if (key.equals(selectedKey) && current != null && current.state != STATE_ERROR) {
      return;
    }
    selectedKey = key;
    ThemePreviewData cached = cache.get(key);
    if (cached != null) {
      preview.setValue(new PreviewState(key, STATE_READY, PERCENT, cached));
      return;
    }
    preview.setValue(new PreviewState(key, STATE_DOWNLOADING, 0, null));
    String url = item.linkdownload();
    String name = item.name();
    io.execute(() -> load(key, url, name, previewWidthPx));
  }

  /** Marks the last install result as handled so it is not reported again after rotation. */
  @MainThread
  public void consumeInstallResult() {
    installResult.setValue(null);
  }

  /** Loads the markdown description of the selected theme, from cache when possible. */
  @MainThread
  public void loadDescription(@Nullable String url) {
    selectedDescriptionUrl = url;
    if (url == null || url.isEmpty()) {
      description.setValue(null);
      return;
    }
    String cached = descriptions.get(url);
    if (cached != null) {
      description.setValue(cached);
      return;
    }
    description.setValue(null);
    ThemesApi.fetchText(
        getApplication(),
        url,
        new ThemesApi.TextCallbacks() {
          @Override
          public void onSuccess(String content) {
            publishDescription(url, content);
          }

          @Override
          public void onError(String message) {
            publishDescription(url, message);
          }
        });
  }

  private void publishDescription(String url, @Nullable String text) {
    if (cleared || text == null) {
      return;
    }
    descriptions.put(url, text);
    if (url.equals(selectedDescriptionUrl)) {
      description.setValue(text);
    }
  }

  @WorkerThread
  private void load(String key, @Nullable String url, @Nullable String name, int widthPx) {
    if (cleared || !key.equals(selectedKey)) {
      return;
    }
    try {
      File themeFile = previewFile(name);
      if (!themeFile.isFile()) {
        if (url == null || url.isEmpty()) {
          throw new IOException("missing download link");
        }
        clearDirectory(themeFile.getParentFile());
        downloadFile(url, themeFile, key);
        int slash = url.lastIndexOf('/');
        String base = slash >= 0 ? url.substring(0, slash + 1) : ThemesApi.REPO_BASE;
        downloadBackground(themeFile, base);
      }
      String json = FileIOUtils.readFile2String(themeFile);
      if (json == null || json.isEmpty()) {
        throw new IOException("empty theme file");
      }
      GhostTheme theme = new ThemeManager(getApplication()).parseDetached(json);
      ThemePreviewData data = ThemePreviewData.create(theme, themeFile, widthPx);
      cache.put(key, data);
      postIfSelected(new PreviewState(key, STATE_READY, PERCENT, data));
    } catch (IOException | RuntimeException e) {
      Log.w(TAG, "Preview failed for " + name + ": " + e.getMessage());
      postIfSelected(new PreviewState(key, STATE_ERROR, 0, null));
    }
  }

  private void postIfSelected(PreviewState state) {
    if (!cleared && state.key.equals(selectedKey)) {
      preview.postValue(state);
    }
  }

  /** Downloaded copy of a theme: one folder per theme so wallpapers never overwrite each other. */
  File previewFile(@Nullable String name) {
    String safe = safeName(name);
    File dir = new File(new File(getApplication().getCacheDir(), PREVIEW_DIR), safe);
    return new File(dir, safe + THEME_EXTENSION);
  }

  /**
   * Where {@link #install} puts the theme on shared storage; unchanged from the original store
   * installer so already installed themes are recognised.
   */
  static File installTarget(String name) {
    File themesDir = new File(Environment.getExternalStorageDirectory(), THEMES_DIR);
    return new File(new File(themesDir, safeName(name)), name + THEME_EXTENSION);
  }

  private static String safeName(@Nullable String name) {
    String safe = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    return safe.isEmpty() ? "theme" : safe;
  }

  /**
   * Installs and applies the previewed theme through the existing path: the downloaded {@code
   * .gth} and its wallpaper are copied to {@code ghostide/themes/<name>/}, then {@link
   * ThemeManager#setThemeFromFile(String)} applies it and {@link ThemeInstalledEvent} is posted.
   * Copying runs on the background thread; applying runs on the main thread because it notifies
   * the theme listeners that restyle the visible activities. A confirmed install still completes
   * when the sheet is dismissed meanwhile.
   */
  @MainThread
  public void install(ThemeItem item) {
    if (Boolean.TRUE.equals(installing.getValue())) {
      return;
    }
    String name = item.name() != null ? item.name() : "theme";
    File source = previewFile(item.name());
    installing.setValue(true);
    io.execute(
        () -> {
          File target = installTarget(name);
          boolean copied = false;
          try {
            copied = copyForInstall(source, target);
          } catch (RuntimeException e) {
            Log.w(TAG, "Copy failed for " + name + ": " + e.getMessage());
          }
          boolean ok = copied;
          mainHandler.post(() -> finishInstall(ok, name, target));
        });
  }

  @MainThread
  private void finishInstall(boolean copied, String name, File target) {
    installing.setValue(false);
    if (!copied) {
      installResult.setValue(new InstallResult(false, name));
      return;
    }
    new ThemeManager(getApplication()).setThemeFromFile(target.getAbsolutePath());
    EventBus.getDefault().post(new ThemeInstalledEvent());
    installResult.setValue(new InstallResult(true, name));
  }

  /** Whether {@code item} is the theme the app is using right now. */
  public boolean isActive(ThemeItem item) {
    String active = ThemeManager.getDefault(getApplication()).getThemeFilePath();
    return active != null
        && item.name() != null
        && active.equals(installTarget(item.name()).getAbsolutePath());
  }

  @WorkerThread
  private boolean copyForInstall(File source, File target) {
    if (!source.isFile()) {
      return false;
    }
    File dir = target.getParentFile();
    if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) {
      return false;
    }
    if (!FileUtils.copy(source.getAbsolutePath(), target.getAbsolutePath())) {
      return false;
    }
    copyBackground(source, target);
    return true;
  }

  private void downloadFile(String url, File out, String key) throws IOException {
    File dir = out.getParentFile();
    if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
      throw new IOException("cannot create " + dir);
    }
    File partial = new File(out.getPath() + ".part");
    Request request = new Request.Builder().url(url).get().build();
    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new IOException("HTTP " + response.code());
      }
      long total = response.body().contentLength();
      try (FileOutputStream fos = new FileOutputStream(partial);
          InputStream is = response.body().byteStream()) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        long done = 0;
        int lastPercent = -1;
        while ((read = is.read(buffer)) != -1) {
          if (cleared) {
            throw new IOException("cancelled");
          }
          fos.write(buffer, 0, read);
          done += read;
          if (total > 0) {
            int percent = (int) ((done * PERCENT) / total);
            if (percent != lastPercent) {
              lastPercent = percent;
              postIfSelected(new PreviewState(key, STATE_DOWNLOADING, percent, null));
            }
          }
        }
      }
    }
    if (!partial.renameTo(out)) {
      throw new IOException("cannot move " + partial);
    }
  }

  private void downloadBackground(File themeFile, String themeDirUrl) {
    String imagepath = relativeImagePath(themeFile);
    if (imagepath == null) {
      return;
    }
    String bgUrl = themeDirUrl + imagepath;
    File bg = new File(themeFile.getParentFile(), new File(imagepath).getName());
    Request request = new Request.Builder().url(bgUrl).get().build();
    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        Log.w(TAG, "Wallpaper HTTP " + response.code() + " for " + bgUrl);
        return;
      }
      try (OutputStream fos = new FileOutputStream(bg);
          InputStream is = response.body().byteStream()) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = is.read(buffer)) != -1) {
          fos.write(buffer, 0, read);
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "Wallpaper download failed: " + e.getMessage());
    }
  }

  /**
   * The theme's wallpaper path when it is a plain relative path inside the theme repository, with
   * leading {@code ./} and {@code ../} removed; null for no wallpaper or an absolute/remote one.
   */
  @Nullable
  private static String relativeImagePath(File themeFile) {
    try {
      String json = FileIOUtils.readFile2String(themeFile);
      if (json == null) {
        return null;
      }
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      if (!root.has("widget") || !root.getAsJsonObject("widget").has("imagepath")) {
        return null;
      }
      String imagepath = root.getAsJsonObject("widget").get("imagepath").getAsString();
      if (imagepath == null
          || imagepath.isEmpty()
          || imagepath.startsWith("http")
          || imagepath.startsWith("/")
          || imagepath.startsWith("content:")
          || imagepath.startsWith("file:")) {
        return null;
      }
      while (imagepath.startsWith("../")) {
        imagepath = imagepath.substring(3);
      }
      if (imagepath.startsWith("./")) {
        imagepath = imagepath.substring(2);
      }
      return imagepath;
    } catch (RuntimeException e) {
      Log.w(TAG, "Unreadable theme file: " + e.getMessage());
      return null;
    }
  }

  private static void copyBackground(File srcThemeFile, File targetThemeFile) {
    String imagepath = relativeImagePath(srcThemeFile);
    if (imagepath == null) {
      return;
    }
    String bgName = new File(imagepath).getName();
    File srcBg = new File(srcThemeFile.getParentFile(), bgName);
    if (srcBg.exists()) {
      File targetBg = new File(targetThemeFile.getParentFile(), bgName);
      if (!FileUtils.copy(srcBg.getAbsolutePath(), targetBg.getAbsolutePath())) {
        Log.w(TAG, "Wallpaper copy failed: " + targetBg);
      }
    }
  }

  private static void clearDirectory(@Nullable File dir) {
    if (dir == null || !dir.exists()) {
      return;
    }
    File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File f : files) {
      if (f.isDirectory()) {
        clearDirectory(f);
      }
      if (!f.delete()) {
        Log.w(TAG, "Cannot delete " + f);
      }
    }
  }

  @Override
  protected void onCleared() {
    cleared = true;
    cache.evictAll();
    descriptions.evictAll();
    File root = new File(getApplication().getCacheDir(), PREVIEW_DIR);
    io.execute(() -> clearDirectory(root));
    io.shutdown();
    super.onCleared();
  }
}

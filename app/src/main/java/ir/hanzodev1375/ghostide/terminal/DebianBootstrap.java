package ir.hanzodev1375.ghostide.terminal;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import ir.hanzodev1375.ghostide.R;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * هماهنگ‌کننده‌ی نصب Debian. خودِ دانلود tar.xz مسئولیت شما با PRDownloader هست؛ این کلاس فقط دو
 * کار میکنه: چک اینکه از قبل نصب شده یا نه، و استخراجِ فایلِ دانلودشده رو یه ترد پس‌زمینه.
 */
public final class DebianBootstrap {

  private DebianBootstrap() {}

  private static final String LOG_TAG = "GHOST_DEBIAN_BOOTSTRAP";
  private static final String ROOTFS_DIR_NAME = "rootfs/debian";
  private static final ExecutorService executor = Executors.newSingleThreadExecutor();
  private static final Handler mainHandler = new Handler(Looper.getMainLooper());

  public interface InstallCallback {
    /** روی main thread صدا زده میشه. */
    void onProgress(int extractedEntries);

    /** روی main thread صدا زده میشه. */
    void onSuccess();

    /** روی main thread صدا زده میشه. */
    void onError(Exception e);

    /**
     * Called on the main thread, instead of {@link #onSuccess()} or {@link #onError(Exception)},
     * once a cancelled extraction has stopped and its partial rootfs has been removed.
     */
    default void onCancelled() {}
  }

  public static File getRootfsDir(Context context) {
    return new File(context.getFilesDir(), ROOTFS_DIR_NAME);
  }

  /** چک میکنه rootfs از قبل کامل استخراج شده یا نه (وجودِ /bin/bash به‌عنوان نشونه‌ی نصب کامل). */
  public static boolean isInstalled(Context context) {
    return new File(getRootfsDir(context), "bin/bash").exists();
  }

  public static void uninstall(Context context, Runnable onDone) {
    File rootfs = getRootfsDir(context);
    executor.execute(
        () -> {
          deleteRecursive(rootfs);
          if (onDone != null) mainHandler.post(onDone);
        });
  }

  /**
   * Deletes {@code file} and, if it is a real directory, its content. Symbolic links are removed
   * but never followed: a rootfs may contain links to host paths such as shared storage, and
   * following them would delete the user's files outside the rootfs.
   */
  static void deleteRecursive(File file) {
    if (!Files.isSymbolicLink(file.toPath())) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) deleteRecursive(child);
      }
    }
    if (!file.delete() && file.exists()) {
      Log.w(LOG_TAG, "could not delete " + file.getAbsolutePath());
    }
  }

  /**
   * فایل tar.xz رو رو یه ترد پس‌زمینه استخراج میکنه.
   *
   * <p>نکته: بعضی وقتا tar زیرِ proot با یه warning بی‌ضرر exit code غیرصفر برمیگردونه با اینکه
   * استخراج واقعاً کامل شده (/bin/bash روی دیسک هست). برای همین اگه extract exception بده ولی
   * isInstalled() true باشه، fail واقعی حسابش نمیکنیم — فقط لاگ میکنیم و موفقیت رو گزارش میدیم؛
   * وگرنه کاربر یه "fail" کاذب می‌بینه با اینکه ترمینال Debian واقعاً کار میکنه.
   *
   * <p>Cancelling {@code signal} (on the main thread) stops the decompression and the proot tar
   * process; the partial rootfs and the downloaded archive are then deleted and exactly one {@link
   * InstallCallback#onCancelled()} is delivered instead of a success or error.
   */
  public static void installFromTarXz(
      Context context,
      File downloadedTarXz,
      CancellationSignal signal,
      InstallCallback callback) {
    Context app = context.getApplicationContext();
    File rootfs = getRootfsDir(app);
    executor.execute(
        () -> {
          try {
            RootfsExtractor.extract(
                app,
                downloadedTarXz,
                rootfs,
                signal,
                count -> mainHandler.post(() -> callback.onProgress(count)));
            if (signal.isCanceled()) {
              discardPartialInstall(rootfs, downloadedTarXz, callback);
              return;
            }
            runFirstBootSetup(app, rootfs);
            deleteIfExists(downloadedTarXz);
            deliver(signal, rootfs, downloadedTarXz, callback, callback::onSuccess);
          } catch (IOException e) {
            if (signal.isCanceled()) {
              discardPartialInstall(rootfs, downloadedTarXz, callback);
            } else if (isInstalled(app)) {
              Log.w(
                  LOG_TAG,
                  "extract() ارور داد ولی bin/bash موجوده، به‌عنوان موفقیت ادامه میدیم: "
                      + e.getMessage(),
                  e);
              try {
                runFirstBootSetup(app, rootfs);
              } catch (IOException setupError) {
                Log.w(LOG_TAG, "firstBootSetup بعد از recovery هم fail شد", setupError);
              }
              deleteIfExists(downloadedTarXz);
              deliver(signal, rootfs, downloadedTarXz, callback, callback::onSuccess);
            } else {
              deliver(signal, rootfs, downloadedTarXz, callback, () -> callback.onError(e));
            }
          }
        });
  }

  /**
   * Posts {@code result} to the main thread, unless the install was cancelled before it ran there;
   * in that case the partial install is discarded and {@link InstallCallback#onCancelled()} is
   * delivered instead, so a late cancel is never lost.
   */
  private static void deliver(
      CancellationSignal signal,
      File rootfs,
      File downloadedTarXz,
      InstallCallback callback,
      Runnable result) {
    mainHandler.post(
        () -> {
          if (signal.isCanceled()) {
            executor.execute(() -> discardPartialInstall(rootfs, downloadedTarXz, callback));
          } else {
            result.run();
          }
        });
  }

  private static void discardPartialInstall(
      File rootfs, File downloadedTarXz, InstallCallback callback) {
    deleteIfExists(new File(rootfs, "bin/bash"));
    deleteRecursive(rootfs);
    deleteIfExists(downloadedTarXz);
    mainHandler.post(callback::onCancelled);
  }

  private static void deleteIfExists(File file) {
    if (file.exists() && !file.delete()) {
      Log.w(LOG_TAG, "could not delete " + file.getAbsolutePath());
    }
  }

  private static void runFirstBootSetup(Context context, File rootfsDir) throws IOException {
    File etc = new File(rootfsDir, "etc");
    if (!etc.exists() && !etc.mkdirs()) {
      throw new IOException(context.getString(R.string.terminal_error_cannot_create_etc_dir, etc));
    }

    writeFile(new File(etc, "resolv.conf"), "nameserver 8.8.8.8\nnameserver 8.8.4.4\n");

    writeFile(
        new File(etc, "hosts"),
        "127.0.0.1   localhost\n"
            + "::1         localhost ip6-localhost ip6-loopback\n"
            + "ff02::1     ip6-allnodes\n"
            + "ff02::2     ip6-allrouters\n");

    appendGroupLinesIfMissing(
        new File(etc, "group"),
        new String[] {
          "inet:x:3003",
          "everybody:x:9997",
          "android_app:x:20455",
          "android_debug:x:50455",
          "android_external_storage:x:1077"
        });
  }

  private static void writeFile(File file, String content) throws IOException {
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(content.getBytes(StandardCharsets.UTF_8));
    }
  }

  private static void appendGroupLinesIfMissing(File groupFile, String[] lines) throws IOException {
    String existing = "";
    if (groupFile.exists()) {
      existing = new String(Files.readAllBytes(groupFile.toPath()), StandardCharsets.UTF_8);
    }
    StringBuilder toAppend = new StringBuilder();
    for (String line : lines) {
      String gid = line.substring(line.lastIndexOf(':') + 1);
      boolean alreadyPresent = existing.contains(":" + gid + "\n") || existing.endsWith(":" + gid);
      if (!alreadyPresent) {
        toAppend.append(line).append('\n');
      }
    }
    if (toAppend.length() > 0) {
      try (FileOutputStream out = new FileOutputStream(groupFile, true)) {
        out.write(toAppend.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      }
    }
  }
}

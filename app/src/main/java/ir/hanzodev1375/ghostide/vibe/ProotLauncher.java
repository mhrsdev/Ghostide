package ir.hanzodev1375.ghostide.vibe;

import android.content.Context;
import androidx.annotation.NonNull;
import ir.hanzodev1375.ghostide.terminal.DebianBootstrap;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** Starts commands inside the Debian rootfs through the app's bundled proot binary. */
final class ProotLauncher {

  private static final String TMP_DIR_NAME = "proot-hermes-tmp";

  /** A started child process and the action that removes its temporary files. */
  static final class Started {
    final Process process;
    final Runnable cleanup;

    Started(Process process, Runnable cleanup) {
      this.process = process;
      this.cleanup = cleanup;
    }
  }

  private final Context appContext;

  ProotLauncher(@NonNull Context context) {
    this.appContext = context.getApplicationContext();
  }

  File rootfsDir() {
    return DebianBootstrap.getRootfsDir(appContext);
  }

  boolean isRootfsReady() {
    return DebianBootstrap.isInstalled(appContext);
  }

  File appDataDir() {
    return appContext.getFilesDir().getParentFile();
  }

  /**
   * Starts {@code guestCommand} with {@code guestEnv} inside the rootfs. The child's stdin is a
   * pipe the caller must write to or close; stdout and stderr are merged.
   *
   * @throws IOException if proot is missing or the process cannot be started
   */
  @NonNull
  Started start(Map<String, String> guestEnv, List<String> guestCommand) throws IOException {
    File nativeLibDir = new File(appContext.getApplicationInfo().nativeLibraryDir);
    if (!new File(nativeLibDir, "libproot.so").exists()
        || !new File(nativeLibDir, "libloader.so").exists()) {
      throw new IOException("proot is not available in this build");
    }
    File tmpRoot = new File(appContext.getCacheDir(), TMP_DIR_NAME);
    File tmpDir = new File(tmpRoot, Long.toString(System.nanoTime()));
    if (!tmpDir.mkdirs() && !tmpDir.isDirectory()) {
      throw new IOException("cannot create the proot temp directory");
    }
    ProotCommand.Command command =
        ProotCommand.build(
            new ProotCommand.Spec(
                nativeLibDir,
                rootfsDir(),
                tmpDir,
                appDataDir().getCanonicalFile(),
                "/root",
                guestEnv,
                guestCommand,
                new File(nativeLibDir, "libloader32.so").exists()));
    ProcessBuilder builder = new ProcessBuilder(command.argv);
    builder.redirectErrorStream(true);
    builder.environment().clear();
    builder.environment().putAll(command.hostEnv);
    Runnable cleanup = () -> deleteQuietly(tmpDir);
    try {
      return new Started(builder.start(), cleanup);
    } catch (IOException e) {
      cleanup.run();
      throw e;
    }
  }

  private static void deleteQuietly(File dir) {
    try {
      VibeFiles.deleteRecursively(dir.toPath());
    } catch (IOException e) {
      android.util.Log.w("GhostVibeProot", "could not remove " + dir.getName());
    }
  }
}

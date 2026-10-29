package ir.hanzodev1375.ghostide.vibe;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the command line and host environment that run one command inside the Debian rootfs with
 * proot. The flags mirror the interactive terminal session (ProotSessionFactory) so the agent sees
 * the same filesystem, but nothing here touches Android APIs, so the result is unit-testable.
 */
final class ProotCommand {

  private static final String PROOT_LIBRARY = "libproot.so";
  private static final String LOADER_LIBRARY = "libloader.so";
  private static final String LOADER32_LIBRARY = "libloader32.so";
  private static final String GUEST_PATH =
      "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin";

  /** Inputs of {@link #build}. */
  static final class Spec {
    final File nativeLibDir;
    final File rootfs;
    final File tmpDir;
    final File appDataDir;
    final String guestWorkingDir;
    final Map<String, String> guestEnv;
    final List<String> guestCommand;
    final boolean loader32Present;

    Spec(
        File nativeLibDir,
        File rootfs,
        File tmpDir,
        File appDataDir,
        String guestWorkingDir,
        Map<String, String> guestEnv,
        List<String> guestCommand,
        boolean loader32Present) {
      this.nativeLibDir = nativeLibDir;
      this.rootfs = rootfs;
      this.tmpDir = tmpDir;
      this.appDataDir = appDataDir;
      this.guestWorkingDir = guestWorkingDir;
      this.guestEnv = guestEnv;
      this.guestCommand = guestCommand;
      this.loader32Present = loader32Present;
    }
  }

  /** A host command line and the environment to start it with. */
  static final class Command {
    final List<String> argv;
    final Map<String, String> hostEnv;

    Command(List<String> argv, Map<String, String> hostEnv) {
      this.argv = Collections.unmodifiableList(argv);
      this.hostEnv = Collections.unmodifiableMap(hostEnv);
    }
  }

  private ProotCommand() {}

  /** The minimal guest environment every Hermes command needs, as an insertion-ordered map. */
  static Map<String, String> baseGuestEnv() {
    Map<String, String> env = new LinkedHashMap<>();
    env.put("HOME", "/root");
    env.put("TERM", "dumb");
    env.put("LANG", "C.UTF-8");
    env.put("PATH", GUEST_PATH);
    return env;
  }

  static Command build(Spec spec) {
    List<String> argv = new ArrayList<>();
    argv.add(new File(spec.nativeLibDir, PROOT_LIBRARY).getPath());
    argv.add("--link2symlink");
    argv.add("--kill-on-exit");
    argv.add("--sysvipc");
    argv.add("-L");
    argv.add("-0");
    argv.add("-r");
    argv.add(spec.rootfs.getPath());
    addBind(argv, "/dev");
    addBind(argv, "/dev/urandom:/dev/random");
    addBind(argv, "/proc");
    addBind(argv, "/sys");
    addBind(argv, HermesPaths.SHARED_STORAGE + ":/sdcard");
    addBind(argv, spec.appDataDir.getPath() + ":" + HermesPaths.GUEST_APP_DATA);
    addBind(argv, HermesPaths.SHARED_STORAGE + ":" + HermesPaths.SHARED_STORAGE);
    argv.add("-w");
    argv.add(spec.guestWorkingDir);
    argv.add("/usr/bin/env");
    argv.add("-i");
    for (Map.Entry<String, String> entry : spec.guestEnv.entrySet()) {
      argv.add(entry.getKey() + "=" + entry.getValue());
    }
    argv.addAll(spec.guestCommand);

    Map<String, String> hostEnv = new LinkedHashMap<>();
    hostEnv.put("PROOT_TMP_DIR", spec.tmpDir.getPath());
    hostEnv.put("PROOT_LOADER", new File(spec.nativeLibDir, LOADER_LIBRARY).getPath());
    hostEnv.put("LD_LIBRARY_PATH", spec.nativeLibDir.getPath());
    if (spec.loader32Present) {
      hostEnv.put("PROOT_LOADER_32", new File(spec.nativeLibDir, LOADER32_LIBRARY).getPath());
    }
    return new Command(argv, hostEnv);
  }

  private static void addBind(List<String> argv, String bind) {
    argv.add("-b");
    argv.add(bind);
  }
}

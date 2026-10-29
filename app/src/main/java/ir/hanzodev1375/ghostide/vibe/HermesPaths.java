package ir.hanzodev1375.ghostide.vibe;

import java.io.File;
import java.io.IOException;

/**
 * Where Hermes lives, seen from the host (Android paths into the extracted rootfs) and from the
 * guest (paths inside proot), plus the workspace path translation.
 */
final class HermesPaths {

  static final String GUEST_INSTALL_DIR = "/opt/ghost-hermes";
  static final String GUEST_VENV = GUEST_INSTALL_DIR + "/venv";
  static final String GUEST_HERMES_BIN = GUEST_VENV + "/bin/hermes";
  static final String GUEST_HERMES_HOME = "/root/.ghost-hermes";
  static final String GUEST_APP_DATA = "/ghostide";
  static final String SHARED_STORAGE = "/storage/emulated/0";

  private static final String MARKER_FILE = "installed.version";

  private final File rootfs;

  HermesPaths(File rootfs) {
    this.rootfs = rootfs;
  }

  File hostInstallDir() {
    return hostFile(GUEST_INSTALL_DIR);
  }

  File hostHermesHome() {
    return hostFile(GUEST_HERMES_HOME);
  }

  File hostConfigFile() {
    return new File(hostHermesHome(), "config.yaml");
  }

  File hostMarkerFile() {
    return new File(hostInstallDir(), MARKER_FILE);
  }

  File hostHermesBinary() {
    return hostFile(GUEST_HERMES_BIN);
  }

  private File hostFile(String guestPath) {
    return new File(rootfs, guestPath.substring(1));
  }

  /**
   * Translates a project folder into the path under which the same folder is visible inside proot:
   * shared storage is bound at the same path, the app's private data directory at {@link
   * #GUEST_APP_DATA}.
   *
   * @throws IOException if the folder does not exist, is not a directory, contains control
   *     characters, or lies outside both bound areas (the agent could not reach it)
   */
  static String toGuestPath(File workspace, File appDataDir) throws IOException {
    String canonical = workspace.getCanonicalPath();
    for (int i = 0; i < canonical.length(); i++) {
      if (canonical.charAt(i) < ' ') {
        throw new IOException("workspace path contains control characters");
      }
    }
    if (!new File(canonical).isDirectory()) {
      throw new IOException("workspace is not a directory");
    }
    String data = appDataDir.getCanonicalPath();
    if (isInside(canonical, data)) {
      return GUEST_APP_DATA + canonical.substring(data.length());
    }
    if (isInside(canonical, SHARED_STORAGE)) {
      return canonical;
    }
    throw new IOException("workspace is outside the folders visible to the agent");
  }

  private static boolean isInside(String path, String root) {
    return path.equals(root) || path.startsWith(root + "/");
  }
}

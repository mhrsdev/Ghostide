package ir.hanzodev1375.ghostide.vibe;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link HermesBackend} that installs and runs Hermes inside the Debian rootfs through proot.
 * Secrets reach the server only through its standard input, never through arguments, files or
 * the process list.
 */
final class ProotHermesBackend implements HermesBackend {

  private static final String TAG = "GhostVibeBackend";
  private static final int INSTALL_LOG_LINES = 40;
  /**
   * The shell script that receives {@code NAME=value} lines on standard input until an empty line,
   * exports them, and replaces itself with the Hermes gateway. {@code --replace} takes over a
   * server left behind by a killed app process, which would otherwise block the start with
   * "Another gateway instance is already running".
   */
  static String launchScript(String hermesBinary) {
    return String.join(
        "\n",
        "set -eu",
        "while IFS= read -r line; do",
        "  if [ -z \"$line\" ]; then break; fi",
        "  export \"$line\"",
        "done",
        "exec " + hermesBinary + " gateway run --replace < /dev/null",
        "");
  }

  private final ProotLauncher launcher;
  private final VibeProviderStore store;
  private final HermesPaths paths;

  ProotHermesBackend(Context context, VibeProviderStore store) {
    this.launcher = new ProotLauncher(context);
    this.store = store;
    this.paths = new HermesPaths(launcher.rootfsDir());
  }

  @Override
  public boolean isRootfsReady() {
    return launcher.isRootfsReady();
  }

  @Override
  public boolean isInstalled() {
    try {
      return paths.hostHermesBinary().isFile()
          && HermesInstallSpec.markerContent()
              .equals(
                  new String(Files.readAllBytes(paths.hostMarkerFile().toPath()), StandardCharsets.UTF_8)
                      .trim());
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public void install(ProgressSink progress, OperationToken token) throws HermesBackendException {
    if (!isRootfsReady()) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.ROOTFS_MISSING, "The Debian rootfs is not installed", null);
    }
    Map<String, String> env = ProotCommand.baseGuestEnv();
    env.put("DEBIAN_FRONTEND", "noninteractive");
    ProotLauncher.Started started;
    try {
      started =
          launcher.start(env, Arrays.asList("/bin/bash", "-c", HermesInstallSpec.installScript()));
    } catch (IOException e) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED, "Cannot start the installer: " + e.getMessage(), e);
    }
    Process process = started.process;
    token.setOnCancel(process::destroy);
    ArrayDeque<String> tail = new ArrayDeque<>();
    InstallProgressParser parser = new InstallProgressParser();
    int exitCode;
    try {
      process.getOutputStream().close();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (tail.size() == INSTALL_LOG_LINES) {
            tail.removeFirst();
          }
          tail.addLast(line);
          if (parser.onLine(line)) {
            progress.onProgress(parser.step(), parser.percent());
          }
        }
      }
      exitCode = process.waitFor();
    } catch (IOException e) {
      process.destroyForcibly();
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED,
          "Installer output failed: " + e.getMessage(),
          e,
          new ArrayList<>(tail));
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED, "Installer interrupted", e);
    } finally {
      token.clearOnCancel();
      started.cleanup.run();
    }
    if (token.isCancelled()) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED, "Install cancelled", null);
    }
    if (exitCode != 0) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED,
          "Installer exited with code " + exitCode,
          null,
          new ArrayList<>(tail));
    }
    try {
      VibeFiles.writeAtomically(
          paths.hostMarkerFile().toPath(), HermesInstallSpec.markerContent().getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED, "Cannot record the installation: " + e.getMessage(), e);
    }
    if (!isInstalled()) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INSTALL_FAILED,
          "Installation finished but Hermes is missing",
          null,
          new ArrayList<>(tail));
    }
    progress.onProgress(HermesRuntime.InstallStep.VERIFY, 100);
  }

  @Override
  public HermesServer launch(File workspace, OperationToken token) throws HermesBackendException {
    VibeProviderConfig config = store.load();
    if (config == null) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.NOT_CONFIGURED, "No provider is configured", null);
    }
    String secret = null;
    if (config.getType().needsSecret()) {
      secret = store.readSecret(config.getType());
      if (secret == null) {
        throw new HermesBackendException(
            HermesRuntime.FailureReason.NOT_CONFIGURED, "The provider has no API key", null);
      }
    }
    String guestWorkspace;
    try {
      guestWorkspace = HermesPaths.toGuestPath(workspace, launcher.appDataDir());
    } catch (IOException e) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.INVALID_WORKSPACE, e.getMessage(), e);
    }
    String apiKey;
    int port;
    try {
      apiKey = store.localApiKey();
      HermesConfigWriter.write(
          paths.hostConfigFile().toPath(), HermesConfigWriter.buildYaml(config, guestWorkspace));
      port = pickFreeLoopbackPort();
    } catch (IOException e) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.START_FAILED, "Cannot prepare Hermes: " + e.getMessage(), e);
    }
    Map<String, String> env = ProotCommand.baseGuestEnv();
    env.put("HERMES_HOME", HermesPaths.GUEST_HERMES_HOME);
    env.put("PYTHONUNBUFFERED", "1");
    env.put("API_SERVER_ENABLED", "true");
    env.put("API_SERVER_HOST", "127.0.0.1");
    env.put("API_SERVER_PORT", Integer.toString(port));

    Map<String, String> secretEnv = new LinkedHashMap<>();
    secretEnv.put("API_SERVER_KEY", apiKey);
    String secretName = HermesConfigWriter.secretEnvName(config.getType());
    if (secretName != null && secret != null) {
      secretEnv.put(secretName, secret);
    }
    ProotLauncher.Started started;
    try {
      started = launcher.start(env, Arrays.asList("/bin/bash", "-c", launchScript(HermesPaths.GUEST_HERMES_BIN)));
    } catch (IOException e) {
      throw new HermesBackendException(
          HermesRuntime.FailureReason.START_FAILED, "Cannot start Hermes: " + e.getMessage(), e);
    }
    try {
      writeSecrets(started.process.getOutputStream(), secretEnv);
    } catch (IOException e) {
      started.process.destroyForcibly();
      started.cleanup.run();
      throw new HermesBackendException(
          HermesRuntime.FailureReason.START_FAILED, "Cannot hand the keys to Hermes", e);
    }
    Log.i(TAG, "Hermes process started on port " + port);
    List<String> toMask = new ArrayList<>(secretEnv.values());
    return new ProotHermesServer(
        started.process, "http://127.0.0.1:" + port, apiKey, toMask, started.cleanup);
  }

  @Override
  public void uninstall() throws IOException {
    VibeFiles.deleteRecursively(paths.hostInstallDir().toPath());
    VibeFiles.deleteRecursively(paths.hostHermesHome().toPath());
  }

  /** Writes {@code NAME=value} lines followed by an empty line, then closes the pipe. */
  static void writeSecrets(OutputStream out, Map<String, String> secretEnv) throws IOException {
    try (OutputStream stream = out) {
      StringBuilder block = new StringBuilder();
      for (Map.Entry<String, String> entry : secretEnv.entrySet()) {
        String value = entry.getValue();
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
          throw new IOException("secret contains a line break");
        }
        block.append(entry.getKey()).append('=').append(value).append('\n');
      }
      block.append('\n');
      stream.write(block.toString().getBytes(StandardCharsets.UTF_8));
      stream.flush();
    }
  }

  private static int pickFreeLoopbackPort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    }
  }
}

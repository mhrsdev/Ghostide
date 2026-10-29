package ir.hanzodev1375.ghostide.vibe;

/**
 * What gets installed into the rootfs and the shell script that does it. Hermes is pulled at
 * runtime from PyPI (MIT licensed, never bundled in the APK); the versions are exact pins so every
 * device installs the same, tested protocol.
 */
final class HermesInstallSpec {

  static final String HERMES_VERSION = "0.19.0";
  static final String AIOHTTP_VERSION = "3.14.1";
  static final String ANTHROPIC_VERSION = "0.87.0";
  static final String APT_PACKAGES = "python3 python3-venv ca-certificates git ripgrep";

  static final String STEP_PREFIX = "@@STEP ";

  private static final String PIP_ARGS =
      "--no-cache-dir --disable-pip-version-check --no-input --progress-bar off --only-binary :all:";

  private HermesInstallSpec() {}

  /** The exact marker contents written after a successful install; a different pin forces reinstall. */
  static String markerContent() {
    return HERMES_VERSION + "|" + AIOHTTP_VERSION + "|" + ANTHROPIC_VERSION;
  }

  /** The bash script executed inside the rootfs. It prints {@code @@STEP <NAME>} lines. */
  static String installScript() {
    String python = HermesPaths.GUEST_VENV + "/bin/python";
    return String.join(
        "\n",
        "set -eu",
        "export DEBIAN_FRONTEND=noninteractive",
        "echo '" + STEP_PREFIX + "PREPARE'",
        "mkdir -p /etc/apt/apt.conf.d " + HermesPaths.GUEST_INSTALL_DIR,
        "printf 'APT::Sandbox::User \"root\";\\nAcquire::Retries \"3\";\\n' > /etc/apt/apt.conf.d/99ghost-vibe",
        "dpkg --configure -a || true",
        "echo '" + STEP_PREFIX + "APT_UPDATE'",
        "apt-get update",
        "echo '" + STEP_PREFIX + "APT_INSTALL'",
        "apt-get install -y --no-install-recommends " + APT_PACKAGES,
        "echo '" + STEP_PREFIX + "VENV'",
        "python3 -m venv " + HermesPaths.GUEST_VENV,
        "echo '" + STEP_PREFIX + "PIP'",
        python
            + " -m pip install "
            + PIP_ARGS
            + " 'hermes-agent=="
            + HERMES_VERSION
            + "' 'aiohttp=="
            + AIOHTTP_VERSION
            + "' 'anthropic=="
            + ANTHROPIC_VERSION
            + "'",
        "echo '" + STEP_PREFIX + "VERIFY'",
        python
            + " -c \"from gateway.platforms import api_server as m; assert m.check_api_server_requirements()\"",
        "test -x " + HermesPaths.GUEST_HERMES_BIN,
        "");
  }
}

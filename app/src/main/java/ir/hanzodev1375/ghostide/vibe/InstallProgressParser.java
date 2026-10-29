package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;

/**
 * Derives a coarse install step and percentage from the output lines of {@link
 * HermesInstallSpec#installScript()}. Step markers set fixed milestones; inside the long steps the
 * count of {@code Setting up} (apt) and {@code Collecting} (pip) lines moves the percentage, capped
 * so it never exceeds the next milestone.
 */
final class InstallProgressParser {

  private static final int PERCENT_APT_UPDATE = 6;
  private static final int PERCENT_APT_INSTALL = 12;
  private static final int PERCENT_VENV = 38;
  private static final int PERCENT_PIP = 42;
  private static final int PERCENT_VERIFY = 96;
  private static final int EXPECTED_APT_PACKAGES = 45;
  private static final int EXPECTED_PIP_PACKAGES = 80;

  private HermesRuntime.InstallStep step = HermesRuntime.InstallStep.PREPARE;
  private int percent = 1;
  private int counted;

  /** Consumes one output line; returns true if step or percent changed. */
  boolean onLine(@NonNull String line) {
    HermesRuntime.InstallStep previousStep = step;
    int previousPercent = percent;
    if (line.startsWith(HermesInstallSpec.STEP_PREFIX)) {
      applyMarker(line.substring(HermesInstallSpec.STEP_PREFIX.length()).trim());
    } else if (step == HermesRuntime.InstallStep.APT_INSTALL && line.startsWith("Setting up ")) {
      counted++;
      percent = interpolate(PERCENT_APT_INSTALL, PERCENT_VENV - 1, counted, EXPECTED_APT_PACKAGES);
    } else if (step == HermesRuntime.InstallStep.PIP && line.startsWith("Collecting ")) {
      counted++;
      percent = interpolate(PERCENT_PIP, PERCENT_VERIFY - 1, counted, EXPECTED_PIP_PACKAGES);
    }
    return step != previousStep || percent != previousPercent;
  }

  @NonNull
  HermesRuntime.InstallStep step() {
    return step;
  }

  int percent() {
    return percent;
  }

  private void applyMarker(String name) {
    counted = 0;
    switch (name) {
      case "PREPARE":
        setStep(HermesRuntime.InstallStep.PREPARE, 1);
        break;
      case "APT_UPDATE":
        setStep(HermesRuntime.InstallStep.APT_UPDATE, PERCENT_APT_UPDATE);
        break;
      case "APT_INSTALL":
        setStep(HermesRuntime.InstallStep.APT_INSTALL, PERCENT_APT_INSTALL);
        break;
      case "VENV":
        setStep(HermesRuntime.InstallStep.VENV, PERCENT_VENV);
        break;
      case "PIP":
        setStep(HermesRuntime.InstallStep.PIP, PERCENT_PIP);
        break;
      case "VERIFY":
        setStep(HermesRuntime.InstallStep.VERIFY, PERCENT_VERIFY);
        break;
      default:
        break;
    }
  }

  private void setStep(HermesRuntime.InstallStep newStep, int newPercent) {
    step = newStep;
    percent = Math.max(percent, newPercent);
  }

  private static int interpolate(int from, int to, int done, int expected) {
    int span = to - from;
    return from + Math.min(span, span * done / expected);
  }
}

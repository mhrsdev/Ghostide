package ir.hanzodev1375.ghostide.vibe.ui.setup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;

/**
 * What the Vibe Coding screen has to say about getting the agent ready: three steps (provider,
 * install, start) and one overall {@link Phase}. A pure function of its inputs, so every state can
 * be rendered and tested without a running server.
 */
public final class SetupModel {

  /** Overall readiness, in the order the user has to deal with the problems. */
  public enum Phase {
    /** Hermes is being installed. */
    INSTALLING,
    /** The server is starting. */
    STARTING,
    /** Ready for prompts. */
    READY,
    /** The last install or start failed; see {@link SetupModel#failureReason()}. */
    FAILED,
    /** The Debian environment of the terminal is missing. */
    NEEDS_ROOTFS,
    /** Hermes is not installed. */
    NEEDS_INSTALL,
    /** No usable provider is configured. */
    NEEDS_PROVIDER,
    /** Installed and configured but the server is stopped (the user stopped it). */
    STOPPED
  }

  /** State of one step in the checklist. */
  public enum StepState {
    /** Not started and not blocked. */
    TODO,
    /** Working on it right now. */
    ACTIVE,
    DONE,
    FAILED,
    /** Cannot start yet; the reason is in {@link SetupModel#blockReason()}. */
    BLOCKED
  }

  /** Why the install step cannot start. */
  public enum BlockReason {
    NONE,
    ROOTFS_MISSING,
    OFFLINE
  }

  private final Phase phase;
  private final StepState provider;
  private final StepState install;
  private final StepState start;
  private final BlockReason blockReason;
  private final HermesRuntime.FailureReason failureReason;
  private final HermesRuntime.InstallStep installStep;
  private final int installPercent;

  private SetupModel(
      Phase phase,
      StepState provider,
      StepState install,
      StepState start,
      BlockReason blockReason,
      @Nullable HermesRuntime.FailureReason failureReason,
      @Nullable HermesRuntime.InstallStep installStep,
      int installPercent) {
    this.phase = phase;
    this.provider = provider;
    this.install = install;
    this.start = start;
    this.blockReason = blockReason;
    this.failureReason = failureReason;
    this.installStep = installStep;
    this.installPercent = installPercent;
  }

  /**
   * Computes the model.
   *
   * @param providerReady a provider is saved and has its key if it needs one
   * @param rootfsInstalled the Debian environment exists
   * @param snapshot the runtime state
   * @param workspaceMatches the server was started for this project folder
   * @param online the device has a network connection
   * @param stoppedByUser the user stopped the server and does not want it started again yet
   */
  @NonNull
  public static SetupModel of(
      boolean providerReady,
      boolean rootfsInstalled,
      @NonNull HermesRuntime.Snapshot snapshot,
      boolean workspaceMatches,
      boolean online,
      boolean stoppedByUser) {
    HermesRuntime.State state = snapshot.getState();
    HermesRuntime.FailureReason reason = snapshot.getFailureReason();
    boolean installed = snapshot.isInstalled();
    StepState providerStep = providerReady ? StepState.DONE : StepState.TODO;
    StepState installStep = installed ? StepState.DONE : StepState.TODO;
    StepState startStep = StepState.TODO;
    BlockReason block = BlockReason.NONE;
    Phase phase;

    if (!installed && state != HermesRuntime.State.INSTALLING) {
      if (!rootfsInstalled || reason == HermesRuntime.FailureReason.ROOTFS_MISSING) {
        block = BlockReason.ROOTFS_MISSING;
        installStep = StepState.BLOCKED;
      } else if (!online) {
        block = BlockReason.OFFLINE;
        installStep = StepState.BLOCKED;
      }
    }

    switch (state) {
      case INSTALLING:
        installStep = StepState.ACTIVE;
        phase = Phase.INSTALLING;
        break;
      case STARTING:
        startStep = StepState.ACTIVE;
        phase = Phase.STARTING;
        break;
      case RUNNING:
        if (workspaceMatches) {
          startStep = StepState.DONE;
          phase = providerReady ? Phase.READY : Phase.NEEDS_PROVIDER;
        } else {
          startStep = StepState.ACTIVE;
          phase = Phase.STARTING;
        }
        break;
      case FAILED:
        phase = Phase.FAILED;
        if (reason == HermesRuntime.FailureReason.ROOTFS_MISSING) {
          phase = Phase.NEEDS_ROOTFS;
        } else if (isInstallFailure(reason) || !installed) {
          installStep = StepState.FAILED;
        } else if (reason == HermesRuntime.FailureReason.NOT_CONFIGURED && !providerReady) {
          phase = Phase.NEEDS_PROVIDER;
        } else {
          startStep = StepState.FAILED;
        }
        break;
      case INSTALLED:
        phase = !providerReady ? Phase.NEEDS_PROVIDER : stoppedByUser ? Phase.STOPPED : Phase.STARTING;
        if (phase == Phase.STARTING) {
          startStep = StepState.ACTIVE;
        }
        break;
      case NOT_INSTALLED:
      default:
        phase =
            block == BlockReason.ROOTFS_MISSING ? Phase.NEEDS_ROOTFS : Phase.NEEDS_INSTALL;
        break;
    }
    if (phase == Phase.NEEDS_ROOTFS) {
      block = BlockReason.ROOTFS_MISSING;
      installStep = StepState.BLOCKED;
    }
    return new SetupModel(
        phase,
        providerStep,
        installStep,
        startStep,
        block,
        reason,
        snapshot.getInstallStep(),
        snapshot.getInstallPercent());
  }

  private static boolean isInstallFailure(@Nullable HermesRuntime.FailureReason reason) {
    return reason == HermesRuntime.FailureReason.INSTALL_FAILED
        || reason == HermesRuntime.FailureReason.UNINSTALL_FAILED
        || reason == HermesRuntime.FailureReason.ROOTFS_MISSING;
  }

  @NonNull
  public Phase phase() {
    return phase;
  }

  @NonNull
  public StepState providerStep() {
    return provider;
  }

  @NonNull
  public StepState installStep() {
    return install;
  }

  @NonNull
  public StepState startStep() {
    return start;
  }

  @NonNull
  public BlockReason blockReason() {
    return blockReason;
  }

  /** Why the runtime is FAILED, or {@code null}. */
  @Nullable
  public HermesRuntime.FailureReason failureReason() {
    return failureReason;
  }

  /** The installer phase while installing, otherwise {@code null}. */
  @Nullable
  public HermesRuntime.InstallStep installPhase() {
    return installStep;
  }

  /** Install progress 0..100 while installing, otherwise -1. */
  public int installPercent() {
    return installPercent;
  }
}

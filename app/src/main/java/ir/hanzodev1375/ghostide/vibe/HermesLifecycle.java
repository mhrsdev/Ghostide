package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * The install/start/stop state machine of the local Hermes server.
 *
 * <pre>
 * NOT_INSTALLED --install--> INSTALLING --ok--> INSTALLED --start--> STARTING --healthy--> RUNNING
 *      ^   cancelInstall --------+  | error            ^  stop -------+            |
 *      |                            v                  +----------------------------+ stop
 *      +------ uninstall ------- FAILED <--- error / process exit / health timeout --+
 * </pre>
 *
 * <p>Rules: install and start are single-flight (a second call while one runs is ignored or, for
 * start with another workspace, restarts the server); every operation carries a generation number
 * so a late result of a cancelled or replaced operation can never change the state; blocking work
 * runs on the injected single-thread worker and never on the caller; snapshots are published to
 * listeners on the main thread in order. No Activity or View is referenced.
 */
final class HermesLifecycle {

  static final long HEALTH_POLL_MILLIS = 500;

  private final HermesBackend backend;
  private final Executor worker;
  private final MainThreadPoster main;
  private final LongSupplier clock;
  private final long healthTimeoutMillis;
  private final long progressIntervalMillis;
  private final CopyOnWriteArrayList<HermesRuntime.Listener> listeners =
      new CopyOnWriteArrayList<>();
  private final Object lock = new Object();

  private HermesRuntime.State state;
  private boolean installed;
  private HermesRuntime.InstallStep step;
  private int percent = -1;
  private HermesRuntime.FailureReason failureReason;
  private String failureMessage = "";
  private List<String> logTail = Collections.emptyList();
  private File workspace;
  private HermesServer server;
  private OperationToken token;
  private int generation;
  private long lastProgressPublishAt;

  HermesLifecycle(
      HermesBackend backend,
      Executor worker,
      MainThreadPoster main,
      LongSupplier clock,
      long healthTimeoutMillis,
      long progressIntervalMillis) {
    this.backend = backend;
    this.worker = worker;
    this.main = main;
    this.clock = clock;
    this.healthTimeoutMillis = healthTimeoutMillis;
    this.progressIntervalMillis = progressIntervalMillis;
    this.installed = backend.isInstalled();
    this.state = installed ? HermesRuntime.State.INSTALLED : HermesRuntime.State.NOT_INSTALLED;
  }

  void addListener(@NonNull HermesRuntime.Listener listener) {
    listeners.addIfAbsent(listener);
    HermesRuntime.Snapshot current = snapshot();
    main.post(
        () -> {
          if (listeners.contains(listener)) {
            listener.onStateChanged(current);
          }
        },
        0);
  }

  void removeListener(@NonNull HermesRuntime.Listener listener) {
    listeners.remove(listener);
  }

  @NonNull
  HermesRuntime.Snapshot snapshot() {
    synchronized (lock) {
      return buildSnapshot();
    }
  }

  /** The running server, or {@code null} unless the state is RUNNING. */
  @Nullable
  HermesServer runningServer() {
    synchronized (lock) {
      return state == HermesRuntime.State.RUNNING ? server : null;
    }
  }

  /**
   * Installs Hermes. Allowed from NOT_INSTALLED and FAILED.
   *
   * @return true if an install was started; false if ignored (already installed, installing or
   *     running) or impossible (the Debian rootfs is missing, which moves to FAILED)
   */
  boolean install() {
    OperationToken newToken;
    int gen;
    synchronized (lock) {
      if (state != HermesRuntime.State.NOT_INSTALLED && state != HermesRuntime.State.FAILED) {
        return false;
      }
      if (!backend.isRootfsReady()) {
        setFailed(
            HermesRuntime.FailureReason.ROOTFS_MISSING,
            "The Debian rootfs is not installed",
            Collections.emptyList());
        publish();
        return false;
      }
      gen = ++generation;
      newToken = new OperationToken();
      token = newToken;
      state = HermesRuntime.State.INSTALLING;
      step = HermesRuntime.InstallStep.PREPARE;
      percent = 0;
      clearFailure();
      lastProgressPublishAt = clock.getAsLong();
      publish();
    }
    worker.execute(() -> installJob(gen, newToken));
    return true;
  }

  /** Cancels a running install; the state returns to NOT_INSTALLED. */
  void cancelInstall() {
    OperationToken toCancel;
    synchronized (lock) {
      if (state != HermesRuntime.State.INSTALLING) {
        return;
      }
      generation++;
      toCancel = token;
      token = null;
      state = HermesRuntime.State.NOT_INSTALLED;
      installed = false;
      percent = -1;
      step = null;
      publish();
    }
    if (toCancel != null) {
      toCancel.cancel();
    }
  }

  /**
   * Starts the server for {@code workspace}. Allowed from INSTALLED and FAILED (when installed).
   * If the server already runs or starts for the same workspace this is a no-op returning true; for
   * another workspace the server is restarted.
   *
   * @return false if Hermes is not installed or an install is running
   */
  boolean start(@NonNull File workspace) {
    OperationToken newToken;
    OperationToken oldToken;
    HermesServer oldServer;
    int gen;
    synchronized (lock) {
      boolean active =
          state == HermesRuntime.State.STARTING || state == HermesRuntime.State.RUNNING;
      if (active && workspace.equals(this.workspace)) {
        return true;
      }
      boolean startable =
          active
              || state == HermesRuntime.State.INSTALLED
              || (state == HermesRuntime.State.FAILED && installed);
      if (!startable) {
        return false;
      }
      gen = ++generation;
      oldToken = token;
      oldServer = server;
      newToken = new OperationToken();
      token = newToken;
      server = null;
      this.workspace = workspace;
      state = HermesRuntime.State.STARTING;
      clearFailure();
      publish();
    }
    if (oldToken != null) {
      oldToken.cancel();
    }
    final HermesServer toStop = oldServer;
    worker.execute(() -> startJob(gen, newToken, workspace, toStop));
    return true;
  }

  /** Stops a starting or running server; the state returns to INSTALLED. */
  void stop() {
    OperationToken toCancel;
    HermesServer toStop;
    synchronized (lock) {
      if (state != HermesRuntime.State.STARTING && state != HermesRuntime.State.RUNNING) {
        return;
      }
      generation++;
      toCancel = token;
      toStop = server;
      token = null;
      server = null;
      state = HermesRuntime.State.INSTALLED;
      publish();
    }
    if (toCancel != null) {
      toCancel.cancel();
    }
    if (toStop != null) {
      worker.execute(toStop::stop);
    }
  }

  /** Removes the installation. Allowed from NOT_INSTALLED, INSTALLED and FAILED. */
  boolean uninstall() {
    int gen;
    synchronized (lock) {
      if (state == HermesRuntime.State.INSTALLING
          || state == HermesRuntime.State.STARTING
          || state == HermesRuntime.State.RUNNING) {
        return false;
      }
      gen = ++generation;
      state = HermesRuntime.State.NOT_INSTALLED;
      installed = false;
      percent = -1;
      step = null;
      clearFailure();
      publish();
    }
    worker.execute(
        () -> {
          try {
            backend.uninstall();
          } catch (IOException e) {
            synchronized (lock) {
              if (gen == generation) {
                setFailed(
                    HermesRuntime.FailureReason.UNINSTALL_FAILED,
                    "Could not remove Hermes: " + e.getMessage(),
                    Collections.emptyList());
                publish();
              }
            }
          }
        });
    return true;
  }

  private void installJob(int gen, OperationToken jobToken) {
    HermesBackend.ProgressSink sink =
        (newStep, newPercent) -> onInstallProgress(gen, newStep, newPercent);
    try {
      backend.install(sink, jobToken);
      synchronized (lock) {
        if (gen != generation) {
          return;
        }
        installed = true;
        state = HermesRuntime.State.INSTALLED;
        percent = 100;
        token = null;
        publish();
      }
    } catch (HermesBackendException e) {
      synchronized (lock) {
        if (gen != generation) {
          return;
        }
        token = null;
        installed = backend.isInstalled();
        setFailed(e.getReason(), e.getMessage(), e.getLogTail());
        publish();
      }
    }
  }

  private void onInstallProgress(int gen, HermesRuntime.InstallStep newStep, int newPercent) {
    synchronized (lock) {
      if (gen != generation || state != HermesRuntime.State.INSTALLING) {
        return;
      }
      boolean stepChanged = newStep != step;
      boolean percentChanged = newPercent != percent;
      if (!stepChanged && !percentChanged) {
        return;
      }
      step = newStep;
      percent = newPercent;
      long now = clock.getAsLong();
      if (stepChanged || newPercent >= 100 || now - lastProgressPublishAt >= progressIntervalMillis) {
        lastProgressPublishAt = now;
        publish();
      }
    }
  }

  private void startJob(int gen, OperationToken jobToken, File ws, @Nullable HermesServer old) {
    if (old != null) {
      old.stop();
    }
    if (jobToken.isCancelled()) {
      return;
    }
    HermesServer launched;
    try {
      launched = backend.launch(ws, jobToken);
    } catch (HermesBackendException e) {
      synchronized (lock) {
        if (gen == generation) {
          token = null;
          setFailed(e.getReason(), e.getMessage(), e.getLogTail());
          publish();
        }
      }
      return;
    }
    synchronized (lock) {
      if (gen != generation) {
        worker.execute(launched::stop);
        return;
      }
      server = launched;
    }
    launched.setExitListener(() -> onServerExit(gen, launched));
    awaitHealthy(gen, jobToken, launched);
  }

  private void awaitHealthy(int gen, OperationToken jobToken, HermesServer launched) {
    long deadline = clock.getAsLong() + healthTimeoutMillis;
    while (!jobToken.isCancelled()) {
      if (!launched.isAlive()) {
        failStart(
            gen,
            launched,
            HermesRuntime.FailureReason.PROCESS_EXITED,
            "Hermes exited during startup (code " + launched.exitCode() + ")");
        return;
      }
      if (launched.isHealthy()) {
        synchronized (lock) {
          if (gen == generation && state == HermesRuntime.State.STARTING) {
            state = HermesRuntime.State.RUNNING;
            publish();
          }
        }
        return;
      }
      if (clock.getAsLong() >= deadline) {
        failStart(
            gen,
            launched,
            HermesRuntime.FailureReason.START_TIMEOUT,
            "Hermes did not become ready in time");
        return;
      }
      if (jobToken.sleepUnlessCancelled(HEALTH_POLL_MILLIS)) {
        return;
      }
    }
  }

  private void failStart(
      int gen, HermesServer launched, HermesRuntime.FailureReason reason, String message) {
    List<String> tail = launched.recentLog();
    synchronized (lock) {
      if (gen != generation) {
        return;
      }
      server = null;
      token = null;
      setFailed(reason, message, tail);
      publish();
    }
    launched.stop();
  }

  private void onServerExit(int gen, HermesServer exited) {
    List<String> tail = exited.recentLog();
    synchronized (lock) {
      if (gen != generation || state != HermesRuntime.State.RUNNING) {
        return;
      }
      server = null;
      token = null;
      setFailed(
          HermesRuntime.FailureReason.PROCESS_EXITED,
          "Hermes stopped unexpectedly (code " + exited.exitCode() + ")",
          tail);
      publish();
    }
  }

  private void setFailed(
      HermesRuntime.FailureReason reason, String message, List<String> tail) {
    state = HermesRuntime.State.FAILED;
    failureReason = reason;
    failureMessage = message == null ? "" : message;
    logTail = tail;
    percent = -1;
    step = null;
  }

  private void clearFailure() {
    failureReason = null;
    failureMessage = "";
    logTail = Collections.emptyList();
  }

  private HermesRuntime.Snapshot buildSnapshot() {
    return new HermesRuntime.Snapshot(
        state,
        installed,
        step,
        percent,
        failureReason,
        failureMessage,
        logTail,
        workspace == null ? "" : workspace.getPath());
  }

  private void publish() {
    HermesRuntime.Snapshot snapshot = buildSnapshot();
    main.post(
        () -> {
          for (HermesRuntime.Listener listener : listeners) {
            listener.onStateChanged(snapshot);
          }
        },
        0);
  }
}

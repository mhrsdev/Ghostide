package ir.hanzodev1375.ghostide.vibe;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application-level owner of the local Hermes agent: installs it into the Debian rootfs, starts
 * and stops its API server (a proot child process bound to 127.0.0.1) and hands out agent runs.
 *
 * <p>This is the entry point of the Vibe Coding backend. Typical use from a screen:
 *
 * <pre>
 * HermesRuntime runtime = HermesRuntime.get(context);
 * runtime.addListener(snapshot -&gt; render(snapshot));        // main thread, replays the current state
 * runtime.install();                                         // NOT_INSTALLED -&gt; INSTALLING -&gt; INSTALLED
 * runtime.start(projectFolder);                              // INSTALLED -&gt; STARTING -&gt; RUNNING
 * HermesRunSession run = runtime.startRun("add a login screen", sessionId, null, listener);
 * </pre>
 *
 * <p>The object never references an Activity or View, keeps only the application context, and the
 * server process lives until {@link #stop()} (or until the system kills the app). All listener
 * callbacks run on the main thread; every method returns immediately and does its work on
 * background threads. State rules are documented on {@code HermesLifecycle}.
 *
 * <p>The first {@link #get} call, {@link #install()} and {@link #isRootfsInstalled()} perform a few
 * file-existence checks and one read of a 40-byte marker file on the calling thread; this is
 * deliberate (a fresh state is needed synchronously) and costs microseconds.
 */
public final class HermesRuntime {

  /** Lifecycle state of the local Hermes server. */
  public enum State {
    /** Hermes is not installed in the rootfs. */
    NOT_INSTALLED,
    /** The installer is running; see {@link Snapshot#getInstallPercent()}. */
    INSTALLING,
    /** Installed and stopped. */
    INSTALLED,
    /** The server process is starting and has not answered its health check yet. */
    STARTING,
    /** The server is up; {@link #startRun} works. */
    RUNNING,
    /** The last install or start failed; see {@link Snapshot#getFailureReason()}. */
    FAILED
  }

  /** Why the state is {@link State#FAILED}. */
  public enum FailureReason {
    /** The Debian rootfs is not installed; install it first (terminal feature). */
    ROOTFS_MISSING,
    /** No provider is configured, or its API key is missing. */
    NOT_CONFIGURED,
    /** The installer failed (no network, apt/pip error, out of disk). See the log tail. */
    INSTALL_FAILED,
    /** The project folder does not exist or is not visible inside the rootfs. */
    INVALID_WORKSPACE,
    /** The server process could not be prepared or launched. */
    START_FAILED,
    /** The server did not become ready in time (slow device or crash loop). */
    START_TIMEOUT,
    /** The server process ended (killed by the system, crashed, out of memory). */
    PROCESS_EXITED,
    /** Removing Hermes failed. */
    UNINSTALL_FAILED
  }

  /** Coarse phase of the installer, for a progress label. */
  public enum InstallStep {
    PREPARE,
    APT_UPDATE,
    APT_INSTALL,
    VENV,
    PIP,
    VERIFY
  }

  /** Immutable view of the runtime state. */
  public static final class Snapshot {
    private final State state;
    private final boolean installed;
    private final InstallStep installStep;
    private final int installPercent;
    private final FailureReason failureReason;
    private final String failureMessage;
    private final List<String> logTail;
    private final String workspace;

    Snapshot(
        State state,
        boolean installed,
        InstallStep installStep,
        int installPercent,
        FailureReason failureReason,
        String failureMessage,
        List<String> logTail,
        String workspace) {
      this.state = state;
      this.installed = installed;
      this.installStep = installStep;
      this.installPercent = installPercent;
      this.failureReason = failureReason;
      this.failureMessage = failureMessage;
      this.logTail = Collections.unmodifiableList(logTail);
      this.workspace = workspace;
    }

    @NonNull
    public State getState() {
      return state;
    }

    /** True if Hermes is installed (also while FAILED); decides whether retry means install or start. */
    public boolean isInstalled() {
      return installed;
    }

    /** The current installer phase while INSTALLING, otherwise {@code null}. */
    @Nullable
    public InstallStep getInstallStep() {
      return installStep;
    }

    /** Install progress 0..100 while INSTALLING, otherwise -1. */
    public int getInstallPercent() {
      return installPercent;
    }

    /** The failure cause when FAILED, otherwise {@code null}. */
    @Nullable
    public FailureReason getFailureReason() {
      return failureReason;
    }

    /** A short English diagnostic, empty unless FAILED. Never contains keys. */
    @NonNull
    public String getFailureMessage() {
      return failureMessage;
    }

    /** The last output lines of the failed installer or server, secrets masked. */
    @NonNull
    public List<String> getLogTail() {
      return logTail;
    }

    /** The project folder the server was last started for, or empty. */
    @NonNull
    public String getWorkspace() {
      return workspace;
    }
  }

  /** Receives state snapshots on the main thread. */
  public interface Listener {
    void onStateChanged(@NonNull Snapshot snapshot);
  }

  /** Instructions that orient the agent inside the IDE; pass to {@link #startRun} or write your own. */
  public static final String DEFAULT_INSTRUCTIONS =
      "You are a coding agent running inside the Ghost IDE Android app. The current directory is the"
          + " user's project; read and edit files there with your tools. Prefer small, reviewable"
          + " changes and explain what you changed.";

  private static final String TAG = "GhostVibeRuntime";
  private static final long HEALTH_TIMEOUT_MILLIS = 180_000;
  private static final long PROGRESS_INTERVAL_MILLIS = 250;
  private static final int IO_THREADS = 6;
  private static final int IO_QUEUE = 16;
  private static final long IO_KEEP_ALIVE_SECONDS = 30;

  private static HermesRuntime instance;

  private final HermesLifecycle lifecycle;
  private final Executor io;
  private final MainThreadPoster main;
  private final ProotLauncher launcher;
  private WeakReference<ChatGptAccountLogin> activeLogin;
  private volatile boolean steeringSupported;
  private volatile HermesServer capabilitiesFor;

  /** Returns the process-wide runtime; only the application context is retained. */
  @NonNull
  public static synchronized HermesRuntime get(@NonNull Context context) {
    if (instance == null) {
      instance = new HermesRuntime(context.getApplicationContext());
    }
    return instance;
  }

  private HermesRuntime(Context app) {
    this.main = MainThreadPoster.forMainLooper();
    this.io = newIoExecutor();
    this.launcher = new ProotLauncher(app);
    ExecutorService worker = Executors.newSingleThreadExecutor(daemonFactory("hermes-lifecycle"));
    this.lifecycle =
        new HermesLifecycle(
            new ProotHermesBackend(app, VibeProviderStore.get(app)),
            worker,
            main,
            System::currentTimeMillis,
            HEALTH_TIMEOUT_MILLIS,
            PROGRESS_INTERVAL_MILLIS);
    lifecycle.addListener(this::onInternalState);
  }

  private static ThreadPoolExecutor newIoExecutor() {
    ThreadPoolExecutor pool =
        new ThreadPoolExecutor(
            IO_THREADS,
            IO_THREADS,
            IO_KEEP_ALIVE_SECONDS,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(IO_QUEUE),
            daemonFactory("hermes-io"));
    pool.allowCoreThreadTimeOut(true);
    return pool;
  }

  private static ThreadFactory daemonFactory(String prefix) {
    AtomicInteger counter = new AtomicInteger();
    return runnable -> {
      Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }

  /** The current state. */
  @NonNull
  public Snapshot getSnapshot() {
    return lifecycle.snapshot();
  }

  /**
   * Registers {@code listener}; it immediately receives the current snapshot (posted to the main
   * thread) and every change afterwards. Remove it in {@code onStop}/{@code onDestroy}.
   */
  public void addListener(@NonNull Listener listener) {
    lifecycle.addListener(listener);
  }

  public void removeListener(@NonNull Listener listener) {
    lifecycle.removeListener(listener);
  }

  /** True if the Debian rootfs Hermes needs is installed. */
  public boolean isRootfsInstalled() {
    return launcher.isRootfsReady();
  }

  /**
   * Installs Hermes (single-flight; needs network). Returns false if nothing was started: already
   * installed or busy, or the rootfs is missing (state becomes FAILED/ROOTFS_MISSING).
   */
  public boolean install() {
    return lifecycle.install();
  }

  /** Cancels a running install; partial files are overwritten by the next attempt. */
  public void cancelInstall() {
    lifecycle.cancelInstall();
  }

  /**
   * Starts the server for {@code projectFolder} using the saved {@link VibeProviderStore}
   * configuration. No-op returning true if it already runs or starts for the same folder; another
   * folder restarts the server. Returns false if Hermes is not installed or an install runs.
   */
  public boolean start(@NonNull File projectFolder) {
    return lifecycle.start(projectFolder);
  }

  /** Stops the server (and cancels a start in progress). */
  public void stop() {
    lifecycle.stop();
  }

  /** Removes Hermes and its state; returns false while installing, starting or running. */
  public boolean uninstall() {
    return lifecycle.uninstall();
  }

  /** Same as {@link #startRun(String, String, String, List, HermesRunSession.Listener)} without history. */
  @NonNull
  public HermesRunSession startRun(
      @NonNull String prompt,
      @Nullable String sessionId,
      @Nullable String instructions,
      @NonNull HermesRunSession.Listener listener) {
    return startRun(prompt, sessionId, instructions, null, listener);
  }

  /**
   * Starts an agent run on the running server.
   *
   * @param prompt the user's message; must not be blank
   * @param sessionId conversation key from {@link HermesRunSession#newSessionId()} to continue a
   *     chat across runs, or {@code null} for a one-off run
   * @param instructions extra system instructions (for example {@link #DEFAULT_INSTRUCTIONS}) or
   *     {@code null}
   * @param history the earlier turns of this conversation, oldest first, or {@code null}. The
   *     installed Hermes (0.19) does not remember a conversation by {@code sessionId}, so pass the
   *     transcript you keep; newer Hermes builds accept it too
   * @param listener receives events on the main thread
   * @throws IllegalStateException if the state is not {@link State#RUNNING}
   */
  @NonNull
  public HermesRunSession startRun(
      @NonNull String prompt,
      @Nullable String sessionId,
      @Nullable String instructions,
      @Nullable List<VibeTurn> history,
      @NonNull HermesRunSession.Listener listener) {
    HermesServer server = lifecycle.runningServer();
    if (server == null) {
      throw new IllegalStateException("Hermes is not running");
    }
    HermesRunSession session =
        new HermesRunSession(
            new HermesApiClient(server.baseUrl(), server.apiKey()),
            io,
            main,
            () -> System.nanoTime() / 1_000_000L,
            prompt,
            sessionId,
            instructions,
            history,
            listener,
            HermesRunSession.DEFAULT_BACKOFF_BASE_MILLIS,
            HermesRunSession.DEFAULT_POLL_INTERVAL_MILLIS);
    session.start();
    return session;
  }

  /** True if the running server can steer runs; false when stopped or on older Hermes builds. */
  public boolean supportsSteering() {
    return steeringSupported;
  }

  /**
   * Starts the ChatGPT account sign-in. A login already in progress is cancelled first. Requires an
   * installed Hermes; otherwise {@code onFailed(NOT_INSTALLED)} is delivered. The returned handle
   * cancels the flow.
   */
  @NonNull
  public synchronized ChatGptAccountLogin beginChatGptLogin(
      @NonNull ChatGptAccountLogin.Listener listener) {
    ChatGptAccountLogin previous = activeLogin == null ? null : activeLogin.get();
    if (previous != null) {
      previous.cancel();
    }
    ChatGptAccountLogin login =
        new ChatGptAccountLogin(
            () ->
                launcher.start(
                    ChatGptAccountLogin.guestEnvironment(), ChatGptAccountLogin.guestCommand()),
            io,
            main,
            listener);
    activeLogin = new WeakReference<>(login);
    if (!lifecycle.snapshot().isInstalled()) {
      main.post(
          () -> listener.onFailed(ChatGptAccountLogin.Failure.NOT_INSTALLED, ""), 0);
      return login;
    }
    login.start();
    return login;
  }

  private void onInternalState(Snapshot snapshot) {
    if (snapshot.getState() != State.RUNNING) {
      steeringSupported = false;
      capabilitiesFor = null;
      return;
    }
    HermesServer server = lifecycle.runningServer();
    if (server == null || server == capabilitiesFor) {
      return;
    }
    capabilitiesFor = server;
    try {
      io.execute(() -> probeCapabilities(server));
    } catch (RejectedExecutionException e) {
      Log.w(TAG, "capabilities probe skipped: background pool is busy");
    }
  }

  private void probeCapabilities(HermesServer server) {
    try {
      HermesApiClient.Capabilities capabilities =
          new HermesApiClient(server.baseUrl(), server.apiKey()).capabilities();
      if (server == capabilitiesFor) {
        steeringSupported = capabilities.supportsSteer();
      }
    } catch (HermesApiException e) {
      Log.w(TAG, "capabilities unavailable: " + e.getKind());
    }
  }
}

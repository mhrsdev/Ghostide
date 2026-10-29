package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * One agent run as seen by the UI: start it, receive events, answer approvals, stop it.
 *
 * <p>Create it with {@link HermesRuntime#startRun}. All {@link Listener} callbacks arrive on the
 * main thread; events are batched (text chunks merged, at most about 20 deliveries per second) and
 * {@link Listener#onFinished} is always the last callback, delivered exactly once. All other
 * methods may be called from any thread and return immediately; network work runs on a shared
 * background pool.
 *
 * <p>The stream is reconnect-safe: if the connection drops, it reconnects (sending the last seen
 * event id on Hermes builds that replay) and, if the server no longer has a stream for the run,
 * falls back to polling the run status until it is terminal. A tool approval that is requested
 * while only polling is possible cannot be shown (the status carries no prompt); such a run waits
 * for Hermes' approval timeout, which denies it.
 */
public final class HermesRunSession {

  /** How a session ended. */
  public enum Outcome {
    /** The agent finished and produced an answer. */
    COMPLETED,
    /** The run failed (provider error, tool crash...); see the {@link VibeEvent.RunFailed}. */
    FAILED,
    /** The run was stopped by the user. */
    CANCELLED,
    /** The Hermes server shut down while the run was active. */
    INTERRUPTED,
    /** The connection to the server was lost and could not be restored. */
    CONNECTION_LOST,
    /** The run could not be created. */
    START_FAILED,
    /** {@link HermesRunSession#close()} was called; the run itself may still be running. */
    CLOSED
  }

  /** Callbacks, all on the main thread. */
  public interface Listener {
    /** The run was created on the server. */
    void onRunStarted(@NonNull String runId, @Nullable String sessionId);

    /** A non-empty, ordered batch of events. */
    void onEvents(@NonNull List<VibeEvent> events);

    /** Approving, stopping or steering failed; the run itself is unaffected. */
    void onControlFailed(@NonNull String action, @NonNull HermesApiException error);

    /**
     * The last callback.
     *
     * @param cause the reason for {@link Outcome#START_FAILED} and {@link Outcome#CONNECTION_LOST},
     *     otherwise {@code null}
     */
    void onFinished(@NonNull Outcome outcome, @Nullable HermesApiException cause);
  }

  static final int MAX_RECONNECT_ATTEMPTS = 6;
  static final long BATCH_INTERVAL_MILLIS = 50;
  static final long DEFAULT_BACKOFF_BASE_MILLIS = 500;
  static final long DEFAULT_POLL_INTERVAL_MILLIS = 1000;
  private static final int MAX_BACKOFF_DOUBLINGS = 3;
  private static final int STATUS_CHECK_EVERY_N_POLL_INTERVALS = 10;

  private final HermesApiClient client;
  private final Executor io;
  private final MainThreadPoster main;
  private final EventBatcher batcher;
  private final Listener listener;
  private final String prompt;
  private final String sessionId;
  private final String instructions;
  private final List<VibeTurn> history;
  private final long backoffBaseMillis;
  private final long pollIntervalMillis;
  private final HermesCancellation streamCancel = new HermesCancellation();
  private final CountDownLatch closedLatch = new CountDownLatch(1);
  private final AtomicBoolean finished = new AtomicBoolean();
  private volatile boolean closed;
  private volatile String runId;
  private VibeEvent terminalEvent;

  HermesRunSession(
      HermesApiClient client,
      Executor io,
      MainThreadPoster main,
      LongSupplier clock,
      String prompt,
      String sessionId,
      String instructions,
      List<VibeTurn> history,
      Listener listener,
      long backoffBaseMillis,
      long pollIntervalMillis) {
    this.client = client;
    this.io = io;
    this.main = main;
    this.listener = listener;
    this.prompt = prompt;
    this.sessionId = sessionId;
    this.instructions = instructions;
    this.history = history;
    this.backoffBaseMillis = backoffBaseMillis;
    this.pollIntervalMillis = pollIntervalMillis;
    this.batcher =
        new EventBatcher(
            main,
            batch -> {
              if (!closed) {
                listener.onEvents(batch);
              }
            },
            BATCH_INTERVAL_MILLIS,
            clock);
  }

  /** Returns a fresh conversation key to pass to {@link HermesRuntime#startRun}. */
  @NonNull
  public static String newSessionId() {
    return "ghost-" + UUID.randomUUID();
  }

  /** The server-side run id, or {@code null} until {@link Listener#onRunStarted} was delivered. */
  @Nullable
  public String getRunId() {
    return runId;
  }

  /** The conversation key this run belongs to, or {@code null} for a one-off run. */
  @Nullable
  public String getSessionId() {
    return sessionId;
  }

  void start() {
    try {
      io.execute(this::runLoop);
    } catch (RejectedExecutionException e) {
      finish(
          Outcome.START_FAILED,
          new HermesApiException(HermesApiException.Kind.UNAVAILABLE, "too many active runs", e));
    }
  }

  /**
   * Answers the pending approval.
   *
   * @param request the {@link VibeEvent.ApprovalRequested} being answered
   * @param decision the user's choice; must be one of {@code request.choices()}
   */
  public void approve(
      @NonNull VibeEvent.ApprovalRequested request, @NonNull HermesApiClient.Decision decision) {
    runControl("approve", () -> client.approve(requireRunId(), request, decision));
  }

  /** Asks the agent to stop. The session ends with {@link Outcome#CANCELLED} when it has. */
  public void stop() {
    runControl("stop", () -> client.stop(requireRunId()));
  }

  /**
   * Sends guidance to the running agent. Reports {@code onControlFailed("steer", UNSUPPORTED)} on
   * Hermes builds that cannot steer; check {@link HermesRuntime#supportsSteering()} first.
   */
  public void steer(@NonNull String text) {
    runControl(
        "steer",
        () -> {
          if (!client.steer(requireRunId(), text)) {
            throw new HermesApiException(
                HermesApiException.Kind.CONFLICT, "the agent did not accept the guidance", null);
          }
        });
  }

  /**
   * Stops listening and delivering. Does not stop the run on the server; call {@link #stop()}
   * first for that. {@code onFinished(CLOSED)} is delivered unless the session already finished.
   */
  public void close() {
    closed = true;
    closedLatch.countDown();
    streamCancel.cancel();
    batcher.close();
    if (finished.compareAndSet(false, true)) {
      main.post(() -> listener.onFinished(Outcome.CLOSED, null), 0);
    }
  }

  private interface Control {
    void run() throws HermesApiException;
  }

  private void runControl(String action, Control control) {
    try {
      io.execute(
          () -> {
            try {
              control.run();
            } catch (HermesApiException e) {
              if (!closed) {
                main.post(() -> listener.onControlFailed(action, e), 0);
              }
            }
          });
    } catch (RejectedExecutionException e) {
      main.post(
          () ->
              listener.onControlFailed(
                  action,
                  new HermesApiException(
                      HermesApiException.Kind.UNAVAILABLE, "too many background tasks", e)),
          0);
    }
  }

  private String requireRunId() throws HermesApiException {
    String id = runId;
    if (id == null) {
      throw new HermesApiException(
          HermesApiException.Kind.CONFLICT, "the run has not been created yet", null);
    }
    return id;
  }

  private void runLoop() {
    HermesApiClient.StartedRun started;
    try {
      started = client.startRun(prompt, sessionId, instructions, history);
    } catch (HermesApiException e) {
      finish(Outcome.START_FAILED, e);
      return;
    }
    runId = started.getRunId();
    if (closed) {
      return;
    }
    main.post(() -> listener.onRunStarted(started.getRunId(), sessionId), 0);
    followRun(started.getRunId());
  }

  private void followRun(String id) {
    long lastSeq = -1;
    int failures = 0;
    HermesApiException lastError = null;
    while (!closed) {
      try {
        HermesApiClient.StreamEnd end =
            client.streamEvents(id, lastSeq, new StreamSink(id), streamCancel);
        lastSeq = Math.max(lastSeq, end.getLastSeq());
        if (end.isTerminalSeen() || terminalEvent != null) {
          finishFromTerminalEvent();
          return;
        }
        if (end.sawEvents()) {
          failures = 0;
        }
      } catch (HermesApiException e) {
        if (closed) {
          return;
        }
        lastError = e;
        if (e.getKind() == HermesApiException.Kind.NOT_FOUND) {
          pollUntilTerminal(id);
          return;
        }
        if (!e.getKind().isRetryable() && e.getKind() != HermesApiException.Kind.PROTOCOL) {
          finish(Outcome.CONNECTION_LOST, e);
          return;
        }
      }
      if (closed) {
        return;
      }
      HermesApiClient.RunStatus status = pollOnce(id);
      if (status != null && status.isTerminal()) {
        emitTerminal(status.toTerminalEvent());
        finishFromTerminalEvent();
        return;
      }
      failures++;
      if (failures > MAX_RECONNECT_ATTEMPTS) {
        finish(Outcome.CONNECTION_LOST, lastError);
        return;
      }
      if (waitClosed(backoff(failures))) {
        return;
      }
    }
  }

  private void pollUntilTerminal(String id) {
    int failures = 0;
    HermesApiException lastError = null;
    while (!closed) {
      try {
        HermesApiClient.RunStatus status = client.getRun(id, streamCancel);
        failures = 0;
        if (status.isTerminal()) {
          emitTerminal(status.toTerminalEvent());
          finishFromTerminalEvent();
          return;
        }
      } catch (HermesApiException e) {
        if (closed) {
          return;
        }
        lastError = e;
        failures++;
        if (failures > MAX_RECONNECT_ATTEMPTS || !e.getKind().isRetryable()) {
          finish(Outcome.CONNECTION_LOST, e);
          return;
        }
      }
      if (waitClosed(pollIntervalMillis)) {
        return;
      }
    }
    if (lastError != null && !closed) {
      finish(Outcome.CONNECTION_LOST, lastError);
    }
  }

  @Nullable
  private HermesApiClient.RunStatus pollOnce(String id) {
    try {
      return client.getRun(id, streamCancel);
    } catch (HermesApiException e) {
      return null;
    }
  }

  private final class StreamSink implements HermesApiClient.EventSink {
    private final String id;
    private long lastStatusCheckAt = System.nanoTime();

    StreamSink(String id) {
      this.id = id;
    }

    @Override
    public boolean onEvent(VibeEvent event) {
      if (closed) {
        return false;
      }
      if (event.isTerminal()) {
        terminalEvent = event;
      }
      batcher.offer(event);
      return true;
    }

    @Override
    public boolean onKeepAlive() {
      if (closed) {
        return false;
      }
      long now = System.nanoTime();
      long interval =
          TimeUnit.MILLISECONDS.toNanos(pollIntervalMillis * STATUS_CHECK_EVERY_N_POLL_INTERVALS);
      if (now - lastStatusCheckAt < interval) {
        return true;
      }
      lastStatusCheckAt = now;
      HermesApiClient.RunStatus status = pollOnce(id);
      if (status != null && status.isTerminal()) {
        emitTerminal(status.toTerminalEvent());
        return false;
      }
      return true;
    }
  }

  private void emitTerminal(VibeEvent event) {
    terminalEvent = event;
    batcher.offer(event);
  }

  private void finishFromTerminalEvent() {
    VibeEvent event = terminalEvent;
    batcher.flushNow();
    if (event instanceof VibeEvent.RunCompleted) {
      finish(Outcome.COMPLETED, null);
    } else if (event instanceof VibeEvent.RunCancelled) {
      finish(Outcome.CANCELLED, null);
    } else if (event instanceof VibeEvent.RunInterrupted) {
      finish(Outcome.INTERRUPTED, null);
    } else {
      finish(Outcome.FAILED, null);
    }
  }

  private void finish(Outcome outcome, @Nullable HermesApiException cause) {
    if (closed || !finished.compareAndSet(false, true)) {
      return;
    }
    batcher.flushNow();
    main.post(() -> listener.onFinished(outcome, cause), 0);
  }

  private boolean waitClosed(long millis) {
    try {
      return closedLatch.await(millis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      closed = true;
      return true;
    }
  }

  long backoff(int failures) {
    return backoffBaseMillis << Math.min(failures - 1, MAX_BACKOFF_DOUBLINGS);
  }
}

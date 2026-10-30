package ir.hanzodev1375.ghostide.vibe.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import ir.hanzodev1375.ghostide.vibe.HermesApiClient;
import ir.hanzodev1375.ghostide.vibe.HermesApiException;
import ir.hanzodev1375.ghostide.vibe.HermesRunSession;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;
import ir.hanzodev1375.ghostide.vibe.VibeEvent;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeTurn;
import ir.hanzodev1375.ghostide.vibe.ui.VibeGateway;
import ir.hanzodev1375.ghostide.vibe.ui.setup.ProviderNetwork;
import ir.hanzodev1375.ghostide.vibe.ui.setup.SetupModel;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Owns the Vibe Coding conversation of one project: the transcript, the active agent run and the
 * readiness of the local agent. It survives rotation; the screen only observes {@link #state()}.
 *
 * <p>Lifecycle decisions:
 *
 * <ul>
 *   <li>The runtime listener is registered in {@link #onUiStarted()} and removed in {@link
 *       #onUiStopped()}. The run listener is owned by this object, never by the Activity, and keeps
 *       updating the transcript while the screen is stopped (for example when the user switches
 *       app); it holds no Activity or View.
 *   <li>Leaving the screen for good ends everything: {@link #onCleared()} stops an active run,
 *       closes its session, stops the local server and saves the transcript. The screen asks the
 *       user to confirm before a run is cut off, so no run is orphaned and no heavy server stays
 *       behind unseen. Rotation does not clear the view model.
 *   <li>The server is started automatically once a provider is saved and Hermes is installed,
 *       unless the user stopped it by hand; it is never restarted automatically after a failure.
 *   <li>Every prompt carries the transcript as history, because the pinned Hermes build ignores
 *       session ids for history.
 * </ul>
 *
 * <p>All public methods must be called on the main thread.
 */
public final class VibeChatViewModel extends ViewModel {

  private static final String TAG = "VibeChat";
  private static final String APPROVE_ACTION = "approve";
  private static final String STOP_ACTION = "stop";

  private final VibeGateway gateway;
  private final File project;
  private final TranscriptStore store;
  private final ExecutorService io;
  private final Executor main;
  private final Supplier<String> sessionIds;
  private final MutableLiveData<ChatUiState> state = new MutableLiveData<>();
  private final HermesRuntime.Listener runtimeListener = this::onSnapshot;
  private final HermesRunSession.Listener runListener = new RunCallbacks();

  private VibeTranscript transcript;
  private HermesRuntime.Snapshot snapshot;
  private VibeProviderConfig provider;
  private boolean providerReady;
  private boolean rootfsInstalled;
  private boolean loaded;
  private boolean cleared;
  private boolean uiStarted;
  private boolean online = true;
  private boolean stoppedByUser;
  private boolean running;
  private boolean stopRequested;
  private boolean filesMayHaveChanged;
  private VibeGateway.Run run;
  private int messageSerial;
  private ChatUiState.UiMessage message;

  /**
   * @param gateway backend access
   * @param project the project folder the agent works in
   * @param store transcript persistence
   * @param io a single-thread executor for file and secret-store access; shut down in {@link
   *     #onCleared()}
   * @param main runs a task on the main thread
   * @param sessionIds creates conversation keys
   */
  public VibeChatViewModel(
      @NonNull VibeGateway gateway,
      @NonNull File project,
      @NonNull TranscriptStore store,
      @NonNull ExecutorService io,
      @NonNull Executor main,
      @NonNull Supplier<String> sessionIds) {
    this.gateway = gateway;
    this.project = project;
    this.store = store;
    this.io = io;
    this.main = main;
    this.sessionIds = sessionIds;
    this.snapshot = gateway.snapshot();
    this.rootfsInstalled = gateway.isRootfsInstalled();
    this.transcript = new VibeTranscript(List.of(), sessionIds.get());
    publish();
    loadAsync();
  }

  /** Factory used by the screen. */
  public static final class Factory implements ViewModelProvider.Factory {
    private final Supplier<VibeGateway> gateway;
    private final File project;
    private final File storeDirectory;

    public Factory(
        @NonNull Supplier<VibeGateway> gateway,
        @NonNull File project,
        @NonNull File storeDirectory) {
      this.gateway = gateway;
      this.project = project;
      this.storeDirectory = storeDirectory;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      if (!modelClass.isAssignableFrom(VibeChatViewModel.class)) {
        throw new IllegalArgumentException("Unknown view model " + modelClass.getName());
      }
      Handler handler = new Handler(Looper.getMainLooper());
      return (T)
          new VibeChatViewModel(
              gateway.get(),
              project,
              new TranscriptStore(storeDirectory),
              Executors.newSingleThreadExecutor(),
              handler::post,
              HermesRunSession::newSessionId);
    }
  }

  /** The current screen state; never null after construction. */
  @NonNull
  public LiveData<ChatUiState> state() {
    return state;
  }

  /** True if files in the project may have been changed by the agent since the screen opened. */
  public boolean filesMayHaveChanged() {
    return filesMayHaveChanged;
  }

  /** Registers the runtime listener; call from {@code onStart}. */
  @MainThread
  public void onUiStarted() {
    if (uiStarted || cleared) {
      return;
    }
    uiStarted = true;
    rootfsInstalled = gateway.isRootfsInstalled();
    gateway.addRuntimeListener(runtimeListener);
  }

  /** Removes the runtime listener and saves the transcript; call from {@code onStop}. */
  @MainThread
  public void onUiStopped() {
    if (!uiStarted) {
      return;
    }
    uiStarted = false;
    gateway.removeRuntimeListener(runtimeListener);
    persist();
  }

  @MainThread
  public void setOnline(boolean value) {
    if (online == value) {
      return;
    }
    online = value;
    publish();
  }

  /** Tells the view model the shown message has been displayed. */
  @MainThread
  public void messageShown(int serial) {
    if (message != null && message.serial() == serial) {
      message = null;
      publish();
    }
  }

  /**
   * Sends {@code text} to the agent.
   *
   * @return false if nothing was sent (blank text, a run is active or the agent is not ready)
   */
  @MainThread
  public boolean send(@NonNull String text) {
    String prompt = text.strip();
    if (prompt.isEmpty() || !currentState().canSend()) {
      return false;
    }
    List<VibeTurn> history = transcript.history();
    transcript.addUser(prompt);
    running = true;
    stopRequested = false;
    try {
      run = gateway.startRun(prompt, transcript.sessionId(), history, runListener);
    } catch (IllegalStateException notRunning) {
      running = false;
      transcript.finish(HermesRunSession.Outcome.START_FAILED, null);
      post(ChatUiState.UiMessage.Kind.AGENT_NOT_RUNNING);
    }
    publish();
    persist();
    return true;
  }

  /** Sends the last user message again, dropping the failed attempt that followed it. */
  @MainThread
  public boolean retry() {
    if (running || !currentState().canSend()) {
      return false;
    }
    String text = transcript.rewindToLastUser();
    if (text == null) {
      return false;
    }
    return send(text);
  }

  /** Asks the active run to stop. */
  @MainThread
  public void stopRun() {
    if (run != null && running && !stopRequested) {
      stopRequested = true;
      run.stop();
      publish();
    }
  }

  /**
   * Answers an approval request.
   *
   * @param itemId the id of the {@link ChatItem.Approval} row
   * @param decision the user's choice; ignored unless the request offers it ({@code deny} is always
   *     allowed)
   */
  @MainThread
  public void answerApproval(long itemId, @NonNull HermesApiClient.Decision decision) {
    ChatItem.Approval approval = transcript.findApproval(itemId);
    if (approval == null
        || run == null
        || approval.request() == null
        || approval.status() != ChatItem.ApprovalStatus.PENDING
        || !isOffered(approval, decision)) {
      return;
    }
    transcript.setApprovalStatus(itemId, ChatItem.ApprovalStatus.SUBMITTING, decision.wireValue());
    run.approve(approval.request(), decision);
    publish();
  }

  private static boolean isOffered(ChatItem.Approval approval, HermesApiClient.Decision decision) {
    if (decision == HermesApiClient.Decision.DENY) {
      return true;
    }
    if (approval.choices().isEmpty()) {
      return decision == HermesApiClient.Decision.ONCE;
    }
    return approval.choices().contains(decision.wireValue());
  }

  /**
   * Starts a new conversation.
   *
   * @return false while a run is active
   */
  @MainThread
  public boolean newChat() {
    if (running) {
      return false;
    }
    transcript.clear(sessionIds.get());
    publish();
    persist();
    return true;
  }

  /** Starts the install of Hermes. */
  @MainThread
  public void install() {
    if (!online) {
      post(ChatUiState.UiMessage.Kind.OFFLINE);
      return;
    }
    stoppedByUser = false;
    rootfsInstalled = gateway.isRootfsInstalled();
    gateway.install();
    publish();
  }

  @MainThread
  public void cancelInstall() {
    gateway.cancelInstall();
  }

  /** Retries whatever failed: the install if Hermes is not installed, otherwise the start. */
  @MainThread
  public void retrySetup() {
    stoppedByUser = false;
    rootfsInstalled = gateway.isRootfsInstalled();
    if (!snapshot.isInstalled()) {
      install();
    } else {
      gateway.start(project);
      publish();
    }
  }

  /** Starts the server after the user stopped it. */
  @MainThread
  public void startAgent() {
    stoppedByUser = false;
    ensureStarted();
    publish();
  }

  /**
   * Stops the local server by the user's wish; it is not started again until {@link
   * #startAgent()}.
   *
   * @return false while a run is active
   */
  @MainThread
  public boolean stopAgent() {
    if (running) {
      return false;
    }
    stoppedByUser = true;
    gateway.stop();
    publish();
    return true;
  }

  /** Re-reads the provider after the provider screen saved changes. */
  @MainThread
  public void providerChanged() {
    if (running) {
      post(ChatUiState.UiMessage.Kind.PROVIDER_CHANGED_BUSY);
      return;
    }
    HermesRuntime.State current = snapshot.getState();
    if (current == HermesRuntime.State.RUNNING || current == HermesRuntime.State.STARTING) {
      gateway.stop();
    }
    stoppedByUser = false;
    loadProviderAsync();
  }

  @Override
  protected void onCleared() {
    cleared = true;
    if (uiStarted) {
      uiStarted = false;
      gateway.removeRuntimeListener(runtimeListener);
    }
    if (run != null) {
      if (running) {
        run.stop();
        transcript.abandonRun(ChatItem.NoticeKind.STOPPED);
      }
      run.close();
      run = null;
      running = false;
    }
    HermesRuntime.State current = snapshot.getState();
    if (current == HermesRuntime.State.RUNNING || current == HermesRuntime.State.STARTING) {
      gateway.stop();
    }
    if (loaded) {
      List<ChatItem> items = transcript.snapshot();
      String sessionId = transcript.sessionId();
      try {
        io.execute(() -> save(items, sessionId, false));
      } catch (RejectedExecutionException e) {
        Log.w(TAG, "transcript was not saved: executor is closed");
      }
    }
    io.shutdown();
  }

  private void onSnapshot(@NonNull HermesRuntime.Snapshot next) {
    if (cleared) {
      return;
    }
    snapshot = next;
    rootfsInstalled = gateway.isRootfsInstalled();
    publish();
    ensureStarted();
  }

  private void ensureStarted() {
    if (!loaded || !providerReady || stoppedByUser || cleared || running || !uiStarted) {
      return;
    }
    HermesRuntime.State current = snapshot.getState();
    boolean mismatch = !snapshot.getWorkspace().equals(project.getPath());
    if (current == HermesRuntime.State.INSTALLED
        || (current == HermesRuntime.State.RUNNING && mismatch)) {
      gateway.start(project);
    }
  }

  private void loadAsync() {
    try {
      io.execute(
          () -> {
            TranscriptStore.Stored stored = store.load(project);
            VibeProviderConfig config = gateway.loadProvider();
            boolean ready = isReady(config);
            main.execute(() -> onLoaded(stored, config, ready));
          });
    } catch (RejectedExecutionException e) {
      Log.w(TAG, "loading skipped: executor is closed");
    }
  }

  private void loadProviderAsync() {
    try {
      io.execute(
          () -> {
            VibeProviderConfig config = gateway.loadProvider();
            boolean ready = isReady(config);
            main.execute(
                () -> {
                  if (cleared) {
                    return;
                  }
                  provider = config;
                  providerReady = ready;
                  publish();
                  ensureStarted();
                });
          });
    } catch (RejectedExecutionException e) {
      Log.w(TAG, "provider refresh skipped: executor is closed");
    }
  }

  private boolean isReady(@Nullable VibeProviderConfig config) {
    return config != null && (!config.getType().needsSecret() || gateway.hasSecret(config.getType()));
  }

  private void onLoaded(
      @Nullable TranscriptStore.Stored stored, @Nullable VibeProviderConfig config, boolean ready) {
    if (cleared) {
      return;
    }
    if (stored != null && transcript.isEmpty()) {
      transcript = new VibeTranscript(stored.items(), stored.sessionId());
      if (stored.runWasOpen()) {
        transcript.abandonRun(ChatItem.NoticeKind.ABANDONED);
      }
    }
    provider = config;
    providerReady = ready;
    loaded = true;
    publish();
    ensureStarted();
  }

  private void post(ChatUiState.UiMessage.Kind kind) {
    message = new ChatUiState.UiMessage(++messageSerial, kind);
    publish();
  }

  private ChatUiState currentState() {
    ChatUiState value = state.getValue();
    return value != null ? value : buildState();
  }

  private void publish() {
    state.setValue(buildState());
  }

  private ChatUiState buildState() {
    boolean workspaceMatches = snapshot.getWorkspace().equals(project.getPath());
    SetupModel setup =
        SetupModel.of(providerReady, rootfsInstalled, snapshot, workspaceMatches, online, stoppedByUser);
    boolean offlineNotice = !online && ProviderNetwork.needsInternet(provider);
    return new ChatUiState(
        transcript.snapshot(),
        setup,
        snapshot,
        provider,
        loaded,
        running,
        running && stopRequested,
        online,
        offlineNotice,
        project.getName(),
        message);
  }

  private void persist() {
    if (cleared || !loaded) {
      return;
    }
    List<ChatItem> items = transcript.snapshot();
    String sessionId = transcript.sessionId();
    boolean open = running;
    try {
      io.execute(() -> save(items, sessionId, open));
    } catch (RejectedExecutionException e) {
      Log.w(TAG, "transcript was not saved: executor is closed");
    }
  }

  private void save(List<ChatItem> items, String sessionId, boolean runOpen) {
    try {
      if (items.isEmpty()) {
        store.delete(project);
      } else {
        store.save(project, sessionId, items, runOpen);
      }
    } catch (IOException e) {
      Log.w(TAG, "transcript could not be saved: " + e.getClass().getSimpleName());
    }
  }

  private final class RunCallbacks implements HermesRunSession.Listener {

    @Override
    public void onRunStarted(@NonNull String runId, @Nullable String sessionId) {
      publish();
    }

    @Override
    public void onEvents(@NonNull List<VibeEvent> events) {
      if (cleared) {
        return;
      }
      transcript.applyEvents(events);
      for (VibeEvent event : events) {
        if (event instanceof VibeEvent.ToolCompleted || event instanceof VibeEvent.RunCompleted) {
          filesMayHaveChanged = true;
        }
      }
      publish();
    }

    @Override
    public void onControlFailed(@NonNull String action, @NonNull HermesApiException error) {
      if (cleared) {
        return;
      }
      if (APPROVE_ACTION.equals(action)) {
        revertSubmittingApprovals();
        post(ChatUiState.UiMessage.Kind.APPROVAL_FAILED);
      } else if (STOP_ACTION.equals(action)) {
        stopRequested = false;
        post(ChatUiState.UiMessage.Kind.STOP_FAILED);
      }
    }

    @Override
    public void onFinished(@NonNull HermesRunSession.Outcome outcome, @Nullable HermesApiException cause) {
      if (cleared || outcome == HermesRunSession.Outcome.CLOSED) {
        return;
      }
      transcript.finish(outcome, cause);
      if (outcome != HermesRunSession.Outcome.START_FAILED) {
        filesMayHaveChanged = true;
      }
      running = false;
      stopRequested = false;
      if (run != null) {
        run.close();
        run = null;
      }
      publish();
      persist();
    }
  }

  private void revertSubmittingApprovals() {
    for (ChatItem item : transcript.snapshot()) {
      if (item instanceof ChatItem.Approval approval
          && approval.status() == ChatItem.ApprovalStatus.SUBMITTING) {
        transcript.setApprovalStatus(approval.id(), ChatItem.ApprovalStatus.PENDING, "");
      }
    }
  }
}

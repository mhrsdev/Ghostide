package ir.hanzodev1375.ghostide.vibe.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.text.BidiFormatter;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.activity.BaseCompat;
import ir.hanzodev1375.ghostide.databinding.ActivityVibeCodingBinding;
import ir.hanzodev1375.ghostide.terminal.InstallProgressStyle;
import ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.utils.ObjectUtil;
import ir.hanzodev1375.ghostide.vibe.HermesApiClient;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;
import ir.hanzodev1375.ghostide.vibe.ui.chat.ChatAdapter;
import ir.hanzodev1375.ghostide.vibe.ui.chat.ChatItem;
import ir.hanzodev1375.ghostide.vibe.ui.chat.ChatUiState;
import ir.hanzodev1375.ghostide.vibe.ui.chat.VibeChatViewModel;
import ir.hanzodev1375.ghostide.vibe.ui.provider.VibeProviderActivity;
import ir.hanzodev1375.ghostide.vibe.ui.setup.SetupModel;
import ir.hanzodev1375.ghostide.vibe.ui.setup.SetupPanel;
import ir.theme.M3Theme;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Agentic coding in a project folder: the user describes a change, the local Hermes agent edits
 * files and runs commands there, and this screen shows its answer, its tool calls and every
 * permission request.
 *
 * <p>Open it with {@link #createIntent}. The conversation, the active run and the readiness of the
 * agent live in {@link VibeChatViewModel}, so rotation changes nothing. Leaving the screen while
 * the agent works asks first and then stops the run and the local server. The activity returns
 * {@link #RESULT_OK} when the agent may have changed files, so the caller can refresh its file
 * list; an editor that has one of those files open notices the change itself on resume.
 */
public class VibeCodingActivity extends BaseCompat {

  /** Extra: absolute path of the project folder. */
  public static final String EXTRA_PROJECT_PATH = "ir.hanzodev1375.ghostide.vibe.PROJECT_PATH";

  private static final float DOCK_RADIUS_DP = 28f;
  private static final float CARD_RADIUS_DP = 16f;
  private static final int MAX_PIN_STEPS = 6;
  private static final int[] SUGGESTIONS = {
    R.string.vibe_suggestion_explain, R.string.vibe_suggestion_bugs, R.string.vibe_suggestion_readme
  };

  private ActivityVibeCodingBinding binding;
  private VibeChatViewModel viewModel;
  private ChatAdapter adapter;
  private SetupPanel setupPanel;
  private NetworkMonitor networkMonitor;
  private VibePalette palette;
  private ChatUiState lastState;
  private File projectFolder;
  private boolean sendStyleDirty = true;
  private boolean lastSendEnabled;
  private boolean rtl;
  private int lastShownMessage;
  private int lastItemCount;
  private final GradientDrawable statusDot = new GradientDrawable();
  private OnBackPressedCallback leaveGuard;
  private ActivityResultLauncher<Intent> providerLauncher;

  /**
   * Creates the intent that opens the screen for {@code project}.
   *
   * @param project an existing project folder
   */
  @NonNull
  public static Intent createIntent(@NonNull Context context, @NonNull File project) {
    return new Intent(context, VibeCodingActivity.class)
        .putExtra(EXTRA_PROJECT_PATH, project.getAbsolutePath())
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
  }

  /** The gateway the screen talks to; tests substitute a double. */
  @NonNull
  protected VibeGateway createGateway() {
    return new RuntimeVibeGateway(this);
  }

  /** The connectivity source; tests substitute a fixed one. */
  @NonNull
  protected NetworkMonitor createNetworkMonitor() {
    return new NetworkMonitor(this);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    File project = resolveProject(getIntent());
    projectFolder = project;
    if (project == null) {
      GhostToast.makeText(this, getString(R.string.vibe_invalid_project), GhostToast.LENGTH_LONG)
          .show();
      finish();
      return;
    }
    binding = ActivityVibeCodingBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    rtl = LocaleDirection.isRtl(this);
    applySafeAreaPadding(binding.vibeRoot, true);
    File chats = new File(getNoBackupFilesDir(), "vibe/chats");
    viewModel =
        new ViewModelProvider(
                this, new VibeChatViewModel.Factory(this::createGateway, project, chats))
            .get(VibeChatViewModel.class);
    providerLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                viewModel.providerChanged();
              }
            });
    setupList();
    setupSetupPanel();
    setupComposer();
    setupTopBar();
    mirrorForReadingDirection();
    setupLeaveGuard();
    M3Theme.apply(binding.getRoot());
    applyLook();
    binding.statusDot.setBackground(statusDot);
    viewModel.state().observe(this, this::render);
  }

  @Nullable
  private static File resolveProject(Intent intent) {
    String path = intent == null ? null : intent.getStringExtra(EXTRA_PROJECT_PATH);
    if (path == null || path.isBlank()) {
      return null;
    }
    try {
      File folder = new File(path).getCanonicalFile();
      return folder.isDirectory() && folder.canRead() ? folder : null;
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (viewModel == null) {
      return;
    }
    viewModel.onUiStarted();
    networkMonitor = createNetworkMonitor();
    networkMonitor.start(viewModel::setOnline);
  }

  @Override
  protected void onStop() {
    if (viewModel != null) {
      if (networkMonitor != null) {
        networkMonitor.stop();
        networkMonitor = null;
      }
      viewModel.onUiStopped();
    }
    super.onStop();
  }

  @Override
  protected void onThemeReapplied() {
    super.onThemeReapplied();
    if (binding != null) {
      applyLook();
      adapter.setPalette(palette);
      if (lastState != null) {
        render(lastState);
      }
    }
  }

  private void setupList() {
    adapter =
        new ChatAdapter(
            this,
            new ChatAdapter.Actions() {
              @Override
              public void onApproval(long itemId, @NonNull HermesApiClient.Decision decision) {
                viewModel.answerApproval(itemId, decision);
              }

              @Override
              public void onRetry() {
                viewModel.retry();
              }

              @Override
              public void onCopy(@NonNull CharSequence text, boolean code) {
                copyToClipboard(text);
              }
            });
    LinearLayoutManager layout = new LinearLayoutManager(this);
    layout.setStackFromEnd(true);
    binding.chatList.setLayoutManager(layout);
    binding.chatList.setAdapter(adapter);
    DefaultItemAnimator animator = new DefaultItemAnimator();
    animator.setSupportsChangeAnimations(false);
    binding.chatList.setItemAnimator(animator);
    binding.chatList.addOnScrollListener(
        new RecyclerView.OnScrollListener() {
          @Override
          public void onScrolled(@NonNull RecyclerView list, int dx, int dy) {
            updateJumpButton();
          }
        });
    binding.btnJumpLatest.setOnClickListener(v -> scrollToLatest());
  }

  private void setupSetupPanel() {
    setupPanel =
        new SetupPanel(
            binding.stepsContainer,
            new SetupPanel.Actions() {
              @Override
              public void onOpenProvider() {
                openProvider();
              }

              @Override
              public void onInstall() {
                viewModel.install();
              }

              @Override
              public void onCancelInstall() {
                viewModel.cancelInstall();
              }

              @Override
              public void onRetry() {
                viewModel.retrySetup();
              }

              @Override
              public void onOpenTerminal() {
                openTerminal();
              }

              @Override
              public void onStartAgent() {
                viewModel.startAgent();
              }
            });
    LayoutInflater inflater = LayoutInflater.from(this);
    for (int resource : SUGGESTIONS) {
      MaterialButton button =
          (MaterialButton)
              inflater.inflate(R.layout.item_vibe_suggestion, binding.suggestionList, false);
      button.setText(resource);
      button.setOnClickListener(
          v -> {
            binding.composerInput.setText(getString(resource));
            binding.composerInput.setSelection(binding.composerInput.length());
            binding.composerInput.requestFocus();
          });
      binding.suggestionList.addView(button);
    }
    binding.readyProject.setText(projectFolder.getPath());
  }

  private void setupComposer() {
    binding.btnSend.setOnClickListener(v -> sendCurrent());
    binding.btnStop.setOnClickListener(v -> viewModel.stopRun());
    binding.providerChip.setOnClickListener(v -> openProvider());
    binding.composerInput.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            updateSendState();
          }
        });
  }

  private void setupTopBar() {
    binding.btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    binding.btnNewChat.setOnClickListener(v -> onNewChat());
    binding.btnMore.setOnClickListener(this::showMenu);
  }

  private void mirrorForReadingDirection() {
    LocaleDirection.mirrorRow(binding.topBar);
    LocaleDirection.mirrorRow(binding.statusRow);
    LocaleDirection.mirrorRow(binding.offlineNotice);
    LocaleDirection.mirrorRow(binding.bannerRow);
    LocaleDirection.mirrorRow(binding.providerChip);
    LocaleDirection.mirrorRow(binding.composerRow);
    binding.btnBack.setScaleX(rtl ? -1f : 1f);
    binding.btnSend.setScaleX(rtl ? -1f : 1f);
    if (rtl) {
      int gravity = Gravity.RIGHT;
      binding.titleText.setGravity(gravity);
      binding.statusText.setGravity(gravity);
      binding.offlineText.setGravity(gravity);
      binding.bannerTitle.setGravity(gravity);
      binding.bannerMessage.setGravity(gravity);
      binding.setupHeadline.setGravity(gravity);
      binding.setupIntro.setGravity(gravity);
      binding.readyHeadline.setGravity(gravity);
      binding.readyBody.setGravity(gravity);
      binding.readyProject.setGravity(Gravity.RIGHT);
      binding.readyTryLabel.setGravity(gravity);
      binding.readyNote.setGravity(gravity);
      binding.composerInput.setGravity(gravity | Gravity.TOP);
      ((LinearLayout.LayoutParams) binding.readyIcon.getLayoutParams()).gravity = gravity;
      ((FrameLayout.LayoutParams) binding.btnJumpLatest.getLayoutParams()).gravity =
          Gravity.BOTTOM | Gravity.LEFT;
      ((LinearLayout.LayoutParams) binding.providerChip.getLayoutParams()).gravity = gravity;
    }
  }

  private void setupLeaveGuard() {
    leaveGuard =
        new OnBackPressedCallback(false) {
          @Override
          public void handleOnBackPressed() {
            confirmLeave();
          }
        };
    getOnBackPressedDispatcher().addCallback(this, leaveGuard);
  }

  private void applyLook() {
    palette = VibePalette.read(this);
    binding.vibeRoot.setBackgroundColor(palette.surface);
    VibeStyle.tint(binding.btnBack, palette.onSurface);
    VibeStyle.tint(binding.btnNewChat, palette.onSurface);
    VibeStyle.tint(binding.btnMore, palette.onSurface);
    VibeStyle.tint(binding.btnJumpLatest, palette.onSurface);
    float card = VibePalette.dp(this, CARD_RADIUS_DP);
    binding.offlineNotice.setBackground(VibePalette.rounded(palette.secondaryContainer, card));
    VibeStyle.tint(binding.offlineIcon, palette.onSecondaryContainer);
    binding.offlineText.setTextColor(palette.onSecondaryContainer);
    binding.composerRow.setBackground(
        VibePalette.rounded(palette.containerHigh, VibePalette.dp(this, DOCK_RADIUS_DP)));
    binding.providerChip.setBackground(VibePalette.rounded(palette.containerHigh, 999f));
    binding.providerChipText.setTextColor(palette.onSurfaceVariant);
    VibeStyle.tint(binding.providerChipIcon, palette.onSurfaceVariant);
    VibeStyle.tint(binding.readyIcon, palette.primary);
    binding.btnJumpLatest.setBackground(VibePalette.rounded(palette.containerHighest, 999f));
    VibeStyle.text(binding.bannerAction, palette);
    for (int i = 0; i < binding.suggestionList.getChildCount(); i++) {
      VibeStyle.tonal((MaterialButton) binding.suggestionList.getChildAt(i), palette);
    }
    statusDot.setShape(GradientDrawable.OVAL);
    binding.loadingIndicator.setIndicatorColor(palette.primary);
    VibeStyle.circleButton(
        binding.btnStop, palette.errorContainer, palette.onErrorContainer, VibePalette.dp(this, 4));
    sendStyleDirty = true;
  }

  private void render(@NonNull ChatUiState state) {
    lastState = state;
    List<ChatItem> items = state.items();
    boolean hasItems = !items.isEmpty();
    SetupModel setup = state.setup();
    SetupModel.Phase phase = setup.phase();
    boolean ready = phase == SetupModel.Phase.READY;
    boolean loaded = state.loaded();

    binding.loadingIndicator.setVisibility(loaded ? View.GONE : View.VISIBLE);
    binding.chatList.setVisibility(loaded && hasItems ? View.VISIBLE : View.GONE);
    binding.introScroll.setVisibility(loaded && !hasItems ? View.VISIBLE : View.GONE);
    binding.setupGroup.setVisibility(!ready ? View.VISIBLE : View.GONE);
    binding.readyGroup.setVisibility(ready ? View.VISIBLE : View.GONE);
    if (loaded && !hasItems && !ready) {
      setupPanel.bind(
          setup, state.provider(), state.snapshot(), state.online(), state.projectName(), palette);
    }

    submitItems(items);
    adapter.setRetryEnabled(state.canSend());
    bindBanner(state, hasItems && loaded && !ready);
    binding.offlineNotice.setVisibility(state.showOfflineNotice() ? View.VISIBLE : View.GONE);
    bindStatus(state);
    bindComposer(state, loaded && (hasItems || ready));
    leaveGuard.setEnabled(state.running());
    setResult(viewModel.filesMayHaveChanged() ? RESULT_OK : RESULT_CANCELED);
    showMessage(state.message());
  }

  private void submitItems(List<ChatItem> items) {
    boolean wasAtBottom = !binding.chatList.canScrollVertically(1);
    boolean grew = items.size() > lastItemCount;
    boolean userJustSent =
        grew && !items.isEmpty() && items.get(items.size() - 1) instanceof ChatItem.UserMessage;
    lastItemCount = items.size();
    adapter.submitList(
        items,
        () -> {
          if (userJustSent || wasAtBottom) {
            scrollToLatest();
          }
          updateJumpButton();
        });
  }

  private void bindBanner(ChatUiState state, boolean visible) {
    binding.runtimeBanner.setVisibility(visible ? View.VISIBLE : View.GONE);
    if (!visible) {
      InstallProgressStyle.apply(binding.bannerProgress, false);
      return;
    }
    SetupModel setup = state.setup();
    boolean failed = setup.phase() == SetupModel.Phase.FAILED;
    binding.runtimeBanner.setBackground(
        VibePalette.rounded(
            failed ? palette.errorContainer : palette.containerHigh,
            VibePalette.dp(this, CARD_RADIUS_DP)));
    int onColor = failed ? palette.onErrorContainer : palette.onSurface;
    binding.bannerTitle.setTextColor(onColor);
    binding.bannerMessage.setTextColor(onColor);
    VibeStyle.tint(binding.bannerIcon, failed ? palette.onErrorContainer : palette.primary);
    binding.bannerIcon.setImageResource(
        failed ? R.drawable.ic_vibe_warning : R.drawable.ic_vibe_sparkle);
    binding.bannerProgress.setVisibility(View.GONE);
    binding.bannerAction.setVisibility(View.VISIBLE);
    binding.bannerAction.setEnabled(true);
    @StringRes int title;
    @StringRes int message = 0;
    String messageText = null;
    @StringRes int action = 0;
    Runnable onAction = null;
    switch (setup.phase()) {
      case INSTALLING:
        title = R.string.vibe_banner_installing;
        messageText = installProgressText(state);
        action = R.string.vibe_action_cancel;
        onAction = viewModel::cancelInstall;
        binding.bannerProgress.setVisibility(View.VISIBLE);
        if (state.snapshot().getInstallPercent() >= 0) {
          binding.bannerProgress.setIndeterminate(false);
          binding.bannerProgress.setProgressCompat(state.snapshot().getInstallPercent(), true);
        } else {
          binding.bannerProgress.setIndeterminate(true);
        }
        break;
      case STARTING:
        title = R.string.vibe_banner_starting;
        message = R.string.vibe_step_start_active;
        binding.bannerProgress.setVisibility(View.VISIBLE);
        binding.bannerProgress.setIndeterminate(true);
        break;
      case FAILED:
        title = R.string.vibe_banner_failed;
        message = SetupPanel.failureText(setup.failureReason());
        action = R.string.vibe_action_retry;
        onAction = viewModel::retrySetup;
        break;
      case NEEDS_ROOTFS:
        title = R.string.vibe_banner_rootfs;
        message = R.string.vibe_step_install_rootfs;
        action = R.string.vibe_action_open_terminal;
        onAction = this::openTerminal;
        break;
      case NEEDS_INSTALL:
        title = R.string.vibe_banner_not_installed;
        message = R.string.vibe_step_install_todo;
        action = R.string.vibe_action_install;
        onAction = viewModel::install;
        binding.bannerAction.setEnabled(state.online());
        break;
      case NEEDS_PROVIDER:
        title = R.string.vibe_banner_provider;
        message = R.string.vibe_step_provider_todo;
        action = R.string.vibe_step_provider_action;
        onAction = this::openProvider;
        break;
      default:
        title = R.string.vibe_banner_stopped;
        message = R.string.vibe_step_start_stopped;
        action = R.string.vibe_action_start;
        onAction = viewModel::startAgent;
        break;
    }
    binding.bannerTitle.setText(title);
    binding.bannerMessage.setText(messageText != null ? messageText : getString(message));
    InstallProgressStyle.apply(binding.bannerProgress, setup.phase() == SetupModel.Phase.INSTALLING);
    if (action == 0) {
      binding.bannerAction.setVisibility(View.GONE);
    } else {
      binding.bannerAction.setText(action);
      Runnable run = onAction;
      binding.bannerAction.setOnClickListener(v -> run.run());
      VibeStyle.textOn(binding.bannerAction, palette, onColor);
    }
  }

  private String installProgressText(ChatUiState state) {
    HermesRuntime.InstallStep step = state.snapshot().getInstallStep();
    int percent = state.snapshot().getInstallPercent();
    String label = getString(installStepLabel(step));
    return percent >= 0 ? getString(R.string.vibe_install_progress, label, percent) : label;
  }

  @StringRes
  private static int installStepLabel(@Nullable HermesRuntime.InstallStep step) {
    if (step == null) {
      return R.string.vibe_install_step_prepare;
    }
    switch (step) {
      case APT_UPDATE:
        return R.string.vibe_install_step_apt_update;
      case APT_INSTALL:
        return R.string.vibe_install_step_apt_install;
      case VENV:
        return R.string.vibe_install_step_venv;
      case PIP:
        return R.string.vibe_install_step_pip;
      case VERIFY:
        return R.string.vibe_install_step_verify;
      default:
        return R.string.vibe_install_step_prepare;
    }
  }

  private void bindStatus(ChatUiState state) {
    SetupModel setup = state.setup();
    int label;
    int dot = palette.onSurfaceVariant;
    String detail = state.projectName();
    switch (setup.phase()) {
      case READY:
        label = state.stopping() ? R.string.vibe_status_stopping
            : state.running() ? R.string.vibe_status_working : R.string.vibe_status_ready;
        dot = palette.primary;
        break;
      case INSTALLING:
        label = R.string.vibe_status_installing;
        dot = palette.primary;
        break;
      case STARTING:
        label = R.string.vibe_status_starting;
        dot = palette.primary;
        break;
      case FAILED:
        label = R.string.vibe_status_failed;
        dot = palette.error;
        break;
      case NEEDS_ROOTFS:
        label = R.string.vibe_status_rootfs;
        break;
      case NEEDS_INSTALL:
        label = R.string.vibe_status_not_installed;
        break;
      case NEEDS_PROVIDER:
        label = R.string.vibe_status_provider;
        break;
      default:
        label = R.string.vibe_status_stopped;
        break;
    }
    statusDot.setColor(dot);
    String name = BidiFormatter.getInstance(rtl).unicodeWrap(detail);
    binding.statusText.setText(getString(R.string.vibe_status_format, getString(label), name));
    binding.statusText.setTextColor(palette.onSurface);
  }

  private void bindComposer(ChatUiState state, boolean visible) {
    binding.composerDock.setVisibility(visible ? View.VISIBLE : View.GONE);
    boolean running = state.running();
    binding.btnSend.setVisibility(running ? View.GONE : View.VISIBLE);
    binding.btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
    binding.btnStop.setEnabled(!state.stopping());
    binding.btnStop.setAlpha(state.stopping() ? 0.38f : 1f);
    binding.composerInput.setEnabled(state.loaded());
    binding.composerInput.setHint(
        state.canSend()
            ? R.string.vibe_composer_hint
            : running ? R.string.vibe_composer_hint_working : R.string.vibe_composer_hint_blocked);
    if (state.provider() != null) {
      binding.providerChip.setVisibility(View.VISIBLE);
      binding.providerChipText.setText(SetupPanel.providerLabel(this, state.provider()));
      binding.providerChip.setEnabled(!running);
      binding.providerChip.setAlpha(running ? 0.5f : 1f);
    } else {
      binding.providerChip.setVisibility(View.GONE);
    }
    updateSendState();
  }

  private void updateSendState() {
    if (lastState == null || palette == null) {
      return;
    }
    boolean enabled = lastState.canSend() && !binding.composerInput.getText().toString().isBlank();
    binding.btnSend.setEnabled(enabled);
    if (!sendStyleDirty && enabled == lastSendEnabled) {
      return;
    }
    sendStyleDirty = false;
    lastSendEnabled = enabled;
    VibeStyle.circleButton(
        binding.btnSend,
        enabled ? palette.primary : palette.containerHighest,
        enabled ? palette.onPrimary : palette.onSurfaceVariant,
        VibePalette.dp(this, 4));
    binding.btnSend.setAlpha(enabled ? 1f : 0.6f);
  }

  private void sendCurrent() {
    String text = binding.composerInput.getText().toString();
    if (viewModel.send(text)) {
      binding.composerInput.setText("");
    }
  }

  private void updateJumpButton() {
    boolean away = binding.chatList.canScrollVertically(1);
    binding.btnJumpLatest.setVisibility(away && adapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
  }

  private void scrollToLatest() {
    int count = adapter.getItemCount();
    if (count == 0) {
      return;
    }
    RecyclerView list = binding.chatList;
    list.scrollToPosition(count - 1);
    list.post(
        () -> {
          for (int i = 0; i < MAX_PIN_STEPS && list.canScrollVertically(1); i++) {
            list.scrollBy(0, list.getHeight());
          }
          updateJumpButton();
        });
  }

  private void showMessage(@Nullable ChatUiState.UiMessage message) {
    if (message == null || message.serial() <= lastShownMessage) {
      return;
    }
    lastShownMessage = message.serial();
    int text;
    switch (message.kind()) {
      case OFFLINE:
        text = R.string.vibe_msg_offline;
        break;
      case APPROVAL_FAILED:
        text = R.string.vibe_msg_approval_failed;
        break;
      case STOP_FAILED:
        text = R.string.vibe_msg_stop_failed;
        break;
      case AGENT_NOT_RUNNING:
        text = R.string.vibe_msg_not_running;
        break;
      default:
        text = R.string.vibe_msg_provider_busy;
        break;
    }
    GhostToast.makeText(this, getString(text), GhostToast.LENGTH_LONG).show();
    viewModel.messageShown(message.serial());
  }

  private void onNewChat() {
    if (lastState != null && lastState.running()) {
      GhostToast.makeText(this, getString(R.string.vibe_msg_stop_first), GhostToast.LENGTH_SHORT).show();
      return;
    }
    if (lastState == null || lastState.items().isEmpty()) {
      return;
    }
    new DialogCompat(this)
        .setTitle(getString(R.string.vibe_new_chat_title))
        .setMessage(getString(R.string.vibe_new_chat_message))
        .setPositiveButton(getString(R.string.vibe_new_chat_confirm), (dialog, which) -> viewModel.newChat())
        .setNegativeButton(getString(R.string.vibe_action_cancel), null)
        .show();
  }

  private void confirmLeave() {
    new DialogCompat(this)
        .setTitle(getString(R.string.vibe_leave_title))
        .setMessage(getString(R.string.vibe_leave_message))
        .setPositiveButton(getString(R.string.vibe_leave_confirm), (dialog, which) -> finish())
        .setNegativeButton(getString(R.string.vibe_leave_stay), null)
        .show();
  }

  private void showMenu(View anchor) {
    List<String> items = new ArrayList<>();
    items.add(getString(R.string.vibe_menu_provider));
    items.add(getString(R.string.vibe_menu_details));
    boolean serverUp =
        lastState != null
            && (lastState.setup().phase() == SetupModel.Phase.READY
                || lastState.setup().phase() == SetupModel.Phase.STARTING);
    items.add(getString(serverUp ? R.string.vibe_menu_stop_agent : R.string.vibe_menu_start_agent));
    items.add(getString(R.string.vibe_menu_terminal));
    ObjectUtil.showGlassMenu(
        this,
        anchor,
        items,
        (index, title) -> {
          switch (index) {
            case 0 -> openProvider();
            case 1 -> showDetails();
            case 2 -> toggleAgent(serverUp);
            case 3 -> openTerminal();
            default -> { }
          }
        });
  }

  private void toggleAgent(boolean serverUp) {
    if (serverUp) {
      if (!viewModel.stopAgent()) {
        GhostToast.makeText(this, getString(R.string.vibe_msg_stop_first), GhostToast.LENGTH_SHORT).show();
      }
    } else {
      viewModel.startAgent();
    }
  }

  private void showDetails() {
    if (lastState == null) {
      return;
    }
    HermesRuntime.Snapshot snapshot = lastState.snapshot();
    StringBuilder text = new StringBuilder();
    text.append(getString(R.string.vibe_details_state, snapshot.getState().name()));
    if (!snapshot.getFailureMessage().isEmpty()) {
      text.append('\n').append(snapshot.getFailureMessage());
    }
    for (String line : snapshot.getLogTail()) {
      text.append('\n').append(line);
    }
    TextView view = new TextView(this);
    view.setTypeface(Typeface.MONOSPACE);
    view.setTextSize(11f);
    view.setTextDirection(View.TEXT_DIRECTION_LTR);
    view.setTextIsSelectable(true);
    view.setPadding(VibePalette.dp(this, 20), VibePalette.dp(this, 8), VibePalette.dp(this, 20), VibePalette.dp(this, 8));
    view.setTextColor(palette.onSurface);
    view.setText(text);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(view);
    new DialogCompat(this)
        .setTitle(getString(R.string.vibe_menu_details))
        .setView(scroll)
        .setPositiveButton(getString(R.string.vibe_action_close), null)
        .show();
  }

  private void openProvider() {
    if (lastState != null && lastState.running()) {
      GhostToast.makeText(this, getString(R.string.vibe_msg_stop_first), GhostToast.LENGTH_SHORT).show();
      return;
    }
    providerLauncher.launch(new Intent(this, VibeProviderActivity.class));
  }

  private void openTerminal() {
    startActivity(new Intent(this, TerminalActivity.class));
  }

  private void copyToClipboard(CharSequence text) {
    ClipboardManager clipboard = getSystemService(ClipboardManager.class);
    if (clipboard == null) {
      return;
    }
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.vibe_title), text));
    GhostToast.makeText(this, getString(R.string.vibe_copied), GhostToast.LENGTH_SHORT).show();
  }
}

package ir.hanzodev1375.ghostide.vibe.ui.setup;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.terminal.InstallProgressStyle;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import ir.hanzodev1375.ghostide.vibe.ui.VibePalette;
import ir.hanzodev1375.ghostide.vibe.ui.VibeStyle;
import ir.theme.M3Theme;

/**
 * The three-step checklist (provider, install, start) shown until the agent is ready. Each step
 * owns its text, progress and buttons; the panel only reads a {@link SetupModel} and never starts
 * anything by itself, every button is forwarded to {@link Actions}.
 */
public final class SetupPanel {

  /** What the buttons of the checklist can ask for. */
  public interface Actions {
    void onOpenProvider();

    void onInstall();

    void onCancelInstall();

    void onRetry();

    void onOpenTerminal();

    void onStartAgent();
  }

  private static final int STEP_COUNT = 3;
  private static final int PROVIDER = 0;
  private static final int INSTALL = 1;
  private static final int START = 2;
  private static final float VALUE_RADIUS_DP = 8f;
  private static final int LOG_WRAP_LINES = 9;
  private static final float LOG_MAX_HEIGHT_DP = 168f;

  private final Actions actions;
  private final StepViews[] steps = new StepViews[STEP_COUNT];
  private final boolean rtl;
  private boolean detailsOpen;
  private HermesRuntime.Snapshot lastSnapshot;
  private SetupModel lastModel;

  /** Inflates the three step rows into {@code container}. */
  public SetupPanel(@NonNull LinearLayout container, @NonNull Actions actions) {
    this.actions = actions;
    this.rtl = LocaleDirection.isRtl(container.getContext());
    LayoutInflater inflater = LayoutInflater.from(container.getContext());
    for (int i = 0; i < STEP_COUNT; i++) {
      View row = inflater.inflate(R.layout.item_vibe_step, container, false);
      container.addView(row);
      steps[i] = new StepViews(row, i + 1);
      M3Theme.listCard(row);
    }
    steps[START].line.setVisibility(View.INVISIBLE);
    steps[PROVIDER].primary.setOnClickListener(v -> actions.onOpenProvider());
    steps[PROVIDER].secondary.setOnClickListener(v -> actions.onOpenProvider());
  }

  /** True while the failure log is expanded. */
  public boolean isDetailsOpen() {
    return detailsOpen;
  }

  /**
   * Updates the checklist.
   *
   * @param provider the saved provider, or {@code null}
   * @param online whether the device has a network connection
   * @param projectName folder name of the project the agent will work in
   */
  public void bind(
      @NonNull SetupModel model,
      @Nullable VibeProviderConfig provider,
      @NonNull HermesRuntime.Snapshot snapshot,
      boolean online,
      @NonNull String projectName,
      @NonNull VibePalette palette) {
    lastModel = model;
    lastSnapshot = snapshot;
    bindProvider(steps[PROVIDER], model, provider, palette);
    bindInstall(steps[INSTALL], model, snapshot, online, palette);
    bindStart(steps[START], model, projectName, palette);
    for (int i = 0; i < STEP_COUNT; i++) {
      steps[i].paintBadge(stateOf(model, i), palette, i);
      steps[i].applyDirection(rtl);
    }
  }

  private static SetupModel.StepState stateOf(SetupModel model, int step) {
    switch (step) {
      case PROVIDER:
        return model.providerStep();
      case INSTALL:
        return model.installStep();
      default:
        return model.startStep();
    }
  }

  private void bindProvider(
      StepViews s, SetupModel model, @Nullable VibeProviderConfig provider, VibePalette p) {
    s.title.setText(R.string.vibe_step_provider_title);
    boolean done = model.providerStep() == SetupModel.StepState.DONE;
    s.clear();
    if (done && provider != null) {
      s.body.setText(R.string.vibe_step_provider_done);
      s.showValue(providerLabel(s.root.getContext(), provider), p);
      s.showActions(null, R.string.vibe_step_provider_change, p);
    } else {
      s.body.setText(R.string.vibe_step_provider_todo);
      s.showActions(R.string.vibe_step_provider_action, null, p);
    }
  }

  private void bindInstall(
      StepViews s, SetupModel model, HermesRuntime.Snapshot snapshot, boolean online, VibePalette p) {
    s.title.setText(R.string.vibe_step_install_title);
    s.clear();
    Context context = s.root.getContext();
    switch (model.installStep()) {
      case DONE:
        s.body.setText(R.string.vibe_step_install_done);
        break;
      case ACTIVE:
        s.body.setText(R.string.vibe_step_install_active);
        s.showProgress(installLabel(context, model), model.installPercent(), true, p);
        s.showActions(null, R.string.vibe_action_cancel, p);
        s.secondary.setOnClickListener(v -> actions.onCancelInstall());
        break;
      case FAILED:
        s.body.setText(failureText(model.failureReason()));
        s.showActions(R.string.vibe_action_retry, R.string.vibe_action_details, p);
        s.primary.setOnClickListener(v -> actions.onRetry());
        s.secondary.setOnClickListener(v -> toggleDetails());
        s.showLog(detailsOpen, logText(snapshot), p);
        break;
      case BLOCKED:
        if (model.blockReason() == SetupModel.BlockReason.ROOTFS_MISSING) {
          s.body.setText(R.string.vibe_step_install_rootfs);
          s.showActions(R.string.vibe_action_open_terminal, null, p);
          s.primary.setOnClickListener(v -> actions.onOpenTerminal());
        } else {
          s.body.setText(R.string.vibe_step_install_offline);
          s.showActions(R.string.vibe_action_install, null, p);
          s.primary.setOnClickListener(v -> actions.onInstall());
          s.primary.setEnabled(online);
        }
        break;
      default:
        s.body.setText(R.string.vibe_step_install_todo);
        s.showActions(R.string.vibe_action_install, null, p);
        s.primary.setOnClickListener(v -> actions.onInstall());
        break;
    }
  }

  private void bindStart(StepViews s, SetupModel model, String projectName, VibePalette p) {
    s.title.setText(R.string.vibe_step_start_title);
    s.clear();
    Context context = s.root.getContext();
    switch (model.startStep()) {
      case DONE:
        s.body.setText(context.getString(R.string.vibe_step_start_done, projectName));
        break;
      case ACTIVE:
        s.body.setText(R.string.vibe_step_start_active);
        s.showProgress(null, -1, true, p);
        break;
      case FAILED:
        s.body.setText(failureText(model.failureReason()));
        s.showActions(R.string.vibe_action_retry, R.string.vibe_action_details, p);
        s.primary.setOnClickListener(v -> actions.onRetry());
        s.secondary.setOnClickListener(v -> toggleDetails());
        s.showLog(detailsOpen, logText(lastSnapshot), p);
        break;
      default:
        if (model.phase() == SetupModel.Phase.STOPPED) {
          s.body.setText(R.string.vibe_step_start_stopped);
          s.showActions(R.string.vibe_action_start, null, p);
          s.primary.setOnClickListener(v -> actions.onStartAgent());
        } else {
          s.body.setText(R.string.vibe_step_start_todo);
        }
        break;
    }
  }

  private void toggleDetails() {
    detailsOpen = !detailsOpen;
    if (lastModel != null && lastSnapshot != null) {
      steps[INSTALL].setLogVisible(detailsOpen);
      steps[START].setLogVisible(detailsOpen);
    }
  }

  private static String logText(@Nullable HermesRuntime.Snapshot snapshot) {
    if (snapshot == null) {
      return "";
    }
    StringBuilder text = new StringBuilder(snapshot.getFailureMessage());
    for (String line : snapshot.getLogTail()) {
      if (text.length() > 0) {
        text.append('\n');
      }
      text.append(line);
    }
    return text.toString();
  }

  /** Localized text for a failure reason. */
  @StringRes
  public static int failureText(@Nullable HermesRuntime.FailureReason reason) {
    if (reason == null) {
      return R.string.vibe_failure_reason_unknown;
    }
    switch (reason) {
      case ROOTFS_MISSING:
        return R.string.vibe_failure_reason_rootfs;
      case NOT_CONFIGURED:
        return R.string.vibe_failure_reason_not_configured;
      case INSTALL_FAILED:
        return R.string.vibe_failure_reason_install;
      case INVALID_WORKSPACE:
        return R.string.vibe_failure_reason_workspace;
      case START_FAILED:
        return R.string.vibe_failure_reason_start;
      case START_TIMEOUT:
        return R.string.vibe_failure_reason_timeout;
      case PROCESS_EXITED:
        return R.string.vibe_failure_reason_exited;
      case UNINSTALL_FAILED:
        return R.string.vibe_failure_reason_uninstall;
      default:
        return R.string.vibe_failure_reason_unknown;
    }
  }

  private static String installLabel(Context context, SetupModel model) {
    HermesRuntime.InstallStep step = model.installPhase();
    int label;
    if (step == null) {
      label = R.string.vibe_install_step_prepare;
    } else {
      switch (step) {
        case APT_UPDATE:
          label = R.string.vibe_install_step_apt_update;
          break;
        case APT_INSTALL:
          label = R.string.vibe_install_step_apt_install;
          break;
        case VENV:
          label = R.string.vibe_install_step_venv;
          break;
        case PIP:
          label = R.string.vibe_install_step_pip;
          break;
        case VERIFY:
          label = R.string.vibe_install_step_verify;
          break;
        default:
          label = R.string.vibe_install_step_prepare;
          break;
      }
    }
    int percent = model.installPercent();
    return percent >= 0
        ? context.getString(R.string.vibe_install_progress, context.getString(label), percent)
        : context.getString(label);
  }

  /** The short provider description shown as a monospace value: "Anthropic · model". */
  @NonNull
  public static String providerLabel(@NonNull Context context, @NonNull VibeProviderConfig config) {
    return context.getString(
        R.string.vibe_provider_label, providerName(context, config.getType()), config.getModel());
  }

  /** The localized name of a provider type. */
  @NonNull
  public static String providerName(@NonNull Context context, @NonNull VibeProviderType type) {
    switch (type) {
      case ANTHROPIC:
        return context.getString(R.string.vibe_provider_anthropic);
      case OPENAI_COMPATIBLE:
        return context.getString(R.string.vibe_provider_openai);
      default:
        return context.getString(R.string.vibe_provider_chatgpt);
    }
  }

  private static final class StepViews {
    final View root;
    final int number;
    final FrameLayout badge;
    final TextView numberView;
    final ImageView icon;
    final View line;
    final LinearLayout content;
    final TextView title;
    final TextView body;
    final TextView value;
    final View progressBox;
    final TextView progressLabel;
    final LinearProgressIndicator progress;
    final View actionsRow;
    final MaterialButton primary;
    final MaterialButton secondary;
    final View logBox;
    final TextView log;
    final GradientDrawable badgeShape = new GradientDrawable();
    final GradientDrawable valueShape = new GradientDrawable();
    boolean mirrored;

    StepViews(View root, int number) {
      this.root = root;
      this.number = number;
      badge = root.findViewById(R.id.stepBadge);
      numberView = root.findViewById(R.id.stepNumber);
      icon = root.findViewById(R.id.stepIcon);
      line = root.findViewById(R.id.stepLine);
      content = root.findViewById(R.id.stepContent);
      title = root.findViewById(R.id.stepTitle);
      body = root.findViewById(R.id.stepBody);
      value = root.findViewById(R.id.stepValue);
      progressBox = root.findViewById(R.id.stepProgressBox);
      progressLabel = root.findViewById(R.id.stepProgressLabel);
      progress = root.findViewById(R.id.stepProgress);
      actionsRow = root.findViewById(R.id.stepActions);
      primary = root.findViewById(R.id.stepPrimary);
      secondary = root.findViewById(R.id.stepSecondary);
      logBox = root.findViewById(R.id.stepLogBox);
      log = root.findViewById(R.id.stepLog);
      badgeShape.setShape(GradientDrawable.OVAL);
      badge.setBackground(badgeShape);
      numberView.setText(String.format(java.util.Locale.getDefault(), "%d", number));
      valueShape.setCornerRadius(VibePalette.dp(root.getContext(), VALUE_RADIUS_DP));
      value.setBackground(valueShape);
    }

    void applyDirection(boolean rtl) {
      if (mirrored || !rtl) {
        return;
      }
      mirrored = true;
      LocaleDirection.mirrorRow((LinearLayout) root);
      LocaleDirection.mirrorRow((LinearLayout) actionsRow);
      ((LinearLayout) actionsRow).setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
      int gravity = Gravity.RIGHT;
      title.setGravity(gravity | Gravity.CENTER_VERTICAL);
      body.setGravity(gravity);
      progressLabel.setGravity(gravity);
      progress.setIndicatorDirection(LinearProgressIndicator.INDICATOR_DIRECTION_RIGHT_TO_LEFT);
      LinearLayout.LayoutParams valueParams = (LinearLayout.LayoutParams) value.getLayoutParams();
      valueParams.gravity = gravity;
    }

    void clear() {
      value.setVisibility(View.GONE);
      progressBox.setVisibility(View.GONE);
      actionsRow.setVisibility(View.GONE);
      logBox.setVisibility(View.GONE);
      primary.setEnabled(true);
      InstallProgressStyle.apply(progress, false);
    }

    void showValue(String text, VibePalette p) {
      value.setText(text);
      value.setTextColor(p.onSurface);
      valueShape.setColor(p.containerHigh);
      value.setVisibility(View.VISIBLE);
    }

    void showProgress(@Nullable String label, int percent, boolean wave, VibePalette p) {
      progressBox.setVisibility(View.VISIBLE);
      progressLabel.setVisibility(label == null ? View.GONE : View.VISIBLE);
      if (label != null) {
        progressLabel.setText(label);
        progressLabel.setTextColor(p.onSurface);
      }
      progress.setVisibility(View.VISIBLE);
      if (percent < 0) {
        if (!progress.isIndeterminate()) {
          progress.setVisibility(View.INVISIBLE);
          progress.setIndeterminate(true);
          progress.setVisibility(View.VISIBLE);
        }
      } else {
        if (progress.isIndeterminate()) {
          progress.setVisibility(View.INVISIBLE);
          progress.setIndeterminate(false);
          progress.setVisibility(View.VISIBLE);
        }
        progress.setProgressCompat(percent, true);
      }
      InstallProgressStyle.apply(progress, wave);
    }

    void showActions(@Nullable @StringRes Integer primaryText, @Nullable @StringRes Integer secondaryText, VibePalette p) {
      actionsRow.setVisibility(View.VISIBLE);
      primary.setVisibility(primaryText == null ? View.GONE : View.VISIBLE);
      secondary.setVisibility(secondaryText == null ? View.GONE : View.VISIBLE);
      if (primaryText != null) {
        primary.setText(primaryText);
        VibeStyle.filled(primary, p);
      }
      if (secondaryText != null) {
        secondary.setText(secondaryText);
        VibeStyle.text(secondary, p);
      }
    }

    void showLog(boolean visible, String text, VibePalette p) {
      log.setText(text);
      log.setTextColor(p.onSurface);
      ViewGroup.LayoutParams boxParams = logBox.getLayoutParams();
      boxParams.height =
          text.split("\n", -1).length <= LOG_WRAP_LINES
              ? ViewGroup.LayoutParams.WRAP_CONTENT
              : VibePalette.dp(root.getContext(), LOG_MAX_HEIGHT_DP);
      logBox.setLayoutParams(boxParams);
      logBox.setBackground(VibePalette.rounded(p.containerLowest, VibePalette.dp(root.getContext(), VALUE_RADIUS_DP)));
      setLogVisible(visible);
    }

    void setLogVisible(boolean visible) {
      if (logBox.getBackground() != null) {
        logBox.setVisibility(visible && log.getText().length() > 0 ? View.VISIBLE : View.GONE);
      }
    }

    void paintBadge(SetupModel.StepState state, VibePalette p, int index) {
      boolean done = state == SetupModel.StepState.DONE;
      boolean failed = state == SetupModel.StepState.FAILED;
      boolean active = state == SetupModel.StepState.ACTIVE;
      int fill = done ? p.primary : failed ? p.errorContainer : active ? p.primaryContainer : p.containerHigh;
      badgeShape.setColor(fill);
      numberView.setVisibility(done || failed ? View.GONE : View.VISIBLE);
      icon.setVisibility(done || failed ? View.VISIBLE : View.GONE);
      if (done) {
        icon.setImageResource(R.drawable.ic_vibe_check);
        VibeStyle.tint(icon, p.onPrimary);
      } else if (failed) {
        icon.setImageResource(R.drawable.ic_vibe_warning);
        VibeStyle.tint(icon, p.onErrorContainer);
      }
      numberView.setTextColor(active ? p.onPrimaryContainer : p.onSurfaceVariant);
      line.setBackgroundColor(done ? p.primary : p.outlineVariant);
      title.setTextColor(p.onSurface);
      body.setTextColor(p.onSurface);
      float alpha = state == SetupModel.StepState.TODO || state == SetupModel.StepState.BLOCKED ? 0.78f : 1f;
      title.setAlpha(index == 0 ? 1f : alpha);
    }
  }
}

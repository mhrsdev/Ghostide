package ir.hanzodev1375.ghostide.vibe.ui.provider;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.textfield.TextInputEditText;
import ir.hanzodev1375.components.sheet.customitemsheet.ui.DialogCompat;
import ir.hanzodev1375.components.views.GhostToast;
import ir.hanzodev1375.ghostide.R;
import ir.hanzodev1375.ghostide.activity.BaseCompat;
import ir.hanzodev1375.ghostide.databinding.ActivityVibeProviderBinding;
import ir.hanzodev1375.ghostide.terminal.InstallProgressStyle;
import ir.hanzodev1375.ghostide.terminal.activity.TerminalActivity;
import ir.hanzodev1375.ghostide.utils.LocaleDirection;
import ir.hanzodev1375.ghostide.vibe.ChatGptAccountLogin;
import ir.hanzodev1375.ghostide.vibe.VibeConfigException;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import ir.hanzodev1375.ghostide.vibe.ui.RuntimeVibeGateway;
import ir.hanzodev1375.ghostide.vibe.ui.VibeGateway;
import ir.hanzodev1375.ghostide.vibe.ui.VibePalette;
import ir.hanzodev1375.ghostide.vibe.ui.VibeStyle;
import ir.theme.M3Theme;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the model provider of the Vibe Coding agent: Anthropic, any OpenAI-compatible endpoint,
 * or a ChatGPT account. The key field is masked, never prefilled and never saved in the instance
 * state; once a key is stored the screen only says so and offers replace or remove. The window is
 * marked secure so the key cannot be captured by screenshots or the recent-apps preview.
 *
 * <p>Result: {@link #RESULT_OK} when the provider was saved.
 */
public class VibeProviderActivity extends BaseCompat {

  private static final String STATE_PREFILLED = "prefilled";
  private static final String STATE_REPLACING = "replacing";
  private static final String STATE_CLEARTEXT_OK = "cleartext_ok";
  private static final float CARD_RADIUS_DP = 16f;
  private static final float FIELD_RADIUS_DP = 12f;
  private static final int TYPE_COUNT = 3;

  private ActivityVibeProviderBinding binding;
  private VibeProviderViewModel viewModel;
  private final List<TypeRow> typeRows = new ArrayList<>();
  private boolean prefilled;
  private boolean replacing;
  private String cleartextConfirmedUrl = "";
  private boolean rtl;
  private VibePalette palette;
  private ProviderUiState lastState;

  /** The gateway the screen talks to; tests substitute a double. */
  @NonNull
  protected VibeGateway createGateway() {
    return new RuntimeVibeGateway(this);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    super.onCreate(savedInstanceState);
    binding = ActivityVibeProviderBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    rtl = LocaleDirection.isRtl(this);
    if (savedInstanceState != null) {
      prefilled = savedInstanceState.getBoolean(STATE_PREFILLED);
      replacing = savedInstanceState.getBoolean(STATE_REPLACING);
      cleartextConfirmedUrl = savedInstanceState.getString(STATE_CLEARTEXT_OK, "");
    }
    applySafeAreaPadding(binding.providerRoot, true);
    viewModel =
        new ViewModelProvider(this, new VibeProviderViewModel.Factory(this::createGateway))
            .get(VibeProviderViewModel.class);
    buildTypeRows();
    setupListeners();
    mirrorForReadingDirection();
    M3Theme.apply(binding.getRoot());
    applyLook();
    viewModel.state().observe(this, this::render);
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean(STATE_PREFILLED, prefilled);
    outState.putBoolean(STATE_REPLACING, replacing);
    outState.putString(STATE_CLEARTEXT_OK, cleartextConfirmedUrl);
  }

  @Override
  protected void onThemeReapplied() {
    super.onThemeReapplied();
    if (binding != null) {
      applyLook();
      if (lastState != null) {
        render(lastState);
      }
    }
  }

  private void buildTypeRows() {
    LayoutInflater inflater = LayoutInflater.from(this);
    VibeProviderType[] types = VibeProviderType.values();
    int[] titles = {
      R.string.vibe_provider_anthropic, R.string.vibe_provider_openai, R.string.vibe_provider_chatgpt
    };
    int[] bodies = {
      R.string.vibe_provider_anthropic_body,
      R.string.vibe_provider_openai_body,
      R.string.vibe_provider_chatgpt_body
    };
    for (int i = 0; i < TYPE_COUNT; i++) {
      View row = inflater.inflate(R.layout.item_vibe_provider_type, binding.typeList, false);
      binding.typeList.addView(row);
      TypeRow typeRow = new TypeRow(row, types[i], titles[i], bodies[i]);
      typeRows.add(typeRow);
      row.setOnClickListener(v -> viewModel.selectType(typeRow.type));
    }
  }

  private void setupListeners() {
    binding.btnBack.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    binding.btnSave.setOnClickListener(v -> onSaveClicked());
    binding.secretReplace.setOnClickListener(
        v -> {
          replacing = true;
          if (lastState != null) {
            render(lastState);
          }
          binding.secretInput.requestFocus();
        });
    binding.secretRemove.setOnClickListener(v -> confirmRemoveSecret());
    binding.accountStart.setOnClickListener(v -> viewModel.beginLogin());
    binding.accountCancel.setOnClickListener(v -> viewModel.cancelLogin());
    binding.accountOpen.setOnClickListener(v -> openVerificationPage());
    binding.accountCopyCode.setOnClickListener(
        v -> copy(binding.accountUserCode.getText(), true));
    binding.accountCopyCommand.setOnClickListener(
        v -> copy(ChatGptAccountLogin.TERMINAL_COMMAND, false));
    binding.accountRunTerminal.setOnClickListener(v -> openTerminal());
    TextWatcher clearErrors =
        new SimpleWatcher(
            () -> {
              binding.urlLayout.setError(null);
              binding.modelLayout.setError(null);
              binding.secretLayout.setError(null);
              updateCleartextWarning();
            });
    binding.urlInput.addTextChangedListener(clearErrors);
    binding.modelInput.addTextChangedListener(clearErrors);
    binding.secretInput.addTextChangedListener(clearErrors);
  }

  private void mirrorForReadingDirection() {
    LocaleDirection.mirrorRow(binding.providerTopBar);
    binding.btnBack.setScaleX(rtl ? -1f : 1f);
    LocaleDirection.mirrorRow(binding.secretSaved);
    LocaleDirection.mirrorRow(binding.accountCodeActions);
    LocaleDirection.mirrorRow(binding.accountActions);
    LocaleDirection.mirrorRow(binding.accountFallbackActions);
    for (TypeRow row : typeRows) {
      LocaleDirection.mirrorRow((LinearLayout) row.root);
    }
    binding.urlLayout.setTypeface(Typeface.DEFAULT);
    binding.modelLayout.setTypeface(Typeface.DEFAULT);
    binding.secretLayout.setTypeface(Typeface.DEFAULT);
    if (rtl) {
      binding.accountCodeActions.setGravity(Gravity.RIGHT);
      binding.accountActions.setGravity(Gravity.RIGHT);
      binding.accountFallbackActions.setGravity(Gravity.RIGHT);
      for (TypeRow row : typeRows) {
        row.title.setGravity(Gravity.RIGHT);
        row.body.setGravity(Gravity.RIGHT);
      }
      int gravity = Gravity.RIGHT;
      binding.providerTitle.setGravity(gravity | Gravity.CENTER_VERTICAL);
      binding.providerIntro.setGravity(gravity);
      binding.accountTitle.setGravity(gravity);
      binding.accountBody.setGravity(gravity);
      binding.accountUrlLabel.setGravity(gravity);
      binding.accountCodeLabel.setGravity(gravity);
      binding.accountFallbackBody.setGravity(gravity);
      binding.saveError.setGravity(gravity);
      binding.cleartextWarning.setGravity(gravity | Gravity.CENTER_VERTICAL);
      binding.secretSavedText.setGravity(gravity);
      LinearLayout.LayoutParams code =
          (LinearLayout.LayoutParams) binding.accountUserCode.getLayoutParams();
      code.gravity = gravity;
    }
  }

  private void applyLook() {
    palette = VibePalette.read(this);
    binding.providerRoot.setBackgroundColor(palette.surface);
    VibeStyle.tint(binding.btnBack, palette.onSurface);
    float card = VibePalette.dp(this, CARD_RADIUS_DP);
    binding.accountGroup.setBackground(VibePalette.rounded(palette.containerLow, card));
    binding.secretSaved.setBackground(VibePalette.rounded(palette.containerHigh, card));
    VibeStyle.tint(binding.secretSavedIcon, palette.primary);
    binding.cleartextWarning.setBackground(VibePalette.rounded(palette.errorContainer, card));
    binding.cleartextWarning.setTextColor(palette.onErrorContainer);
    binding.cleartextWarning.setCompoundDrawablesRelativeWithIntrinsicBounds(
        R.drawable.ic_vibe_warning, 0, 0, 0);
    binding.cleartextWarning.setCompoundDrawableTintList(
        ColorStateList.valueOf(palette.onErrorContainer));
    binding.accountUserCode.setBackground(
        VibePalette.rounded(palette.containerHighest, VibePalette.dp(this, FIELD_RADIUS_DP)));
    binding.accountCommand.setBackground(
        VibePalette.rounded(palette.containerHighest, VibePalette.dp(this, FIELD_RADIUS_DP)));
    binding.saveError.setTextColor(palette.error);
    VibeStyle.filled(binding.btnSave, palette);
    VibeStyle.filled(binding.accountStart, palette);
    VibeStyle.filled(binding.accountOpen, palette);
    VibeStyle.text(binding.accountCancel, palette);
    VibeStyle.text(binding.accountCopyCode, palette);
    VibeStyle.text(binding.accountCopyCommand, palette);
    VibeStyle.tonal(binding.accountRunTerminal, palette);
    VibeStyle.text(binding.secretReplace, palette);
    VibeStyle.text(binding.secretRemove, palette);
    for (TypeRow row : typeRows) {
      row.paint(palette, this, lastState != null && lastState.type() == row.type);
    }
  }

  private void render(@NonNull ProviderUiState state) {
    lastState = state;
    if (!prefilled && state.loaded()) {
      prefilled = true;
      binding.urlInput.setText(state.baseUrl());
      binding.modelInput.setText(state.model());
    }
    VibeProviderType type = state.type();
    boolean needsSecret = type.needsSecret();
    boolean account = type == VibeProviderType.CHATGPT_ACCOUNT;
    for (TypeRow row : typeRows) {
      row.paint(palette, this, row.type == type);
    }
    binding.urlGroup.setVisibility(account ? View.GONE : View.VISIBLE);
    binding.urlLayout.setHelperText(
        getString(
            type == VibeProviderType.OPENAI_COMPATIBLE
                ? R.string.vibe_field_url_helper
                : R.string.vibe_field_url_helper_optional));
    binding.secretGroup.setVisibility(needsSecret && state.secretKnown() ? View.VISIBLE : View.GONE);
    boolean showSaved = needsSecret && state.hasSecret() && !replacing;
    binding.secretSaved.setVisibility(showSaved ? View.VISIBLE : View.GONE);
    binding.secretLayout.setVisibility(needsSecret && !showSaved ? View.VISIBLE : View.GONE);
    binding.accountGroup.setVisibility(account ? View.VISIBLE : View.GONE);
    renderAccount(state);
    renderErrors(state);
    updateCleartextWarning();
    binding.btnSave.setEnabled(!state.saving() && state.loaded());
    binding.btnSave.setText(state.saving() ? R.string.vibe_action_saving : R.string.vibe_action_save);
    if (state.saved()) {
      binding.secretInput.setText("");
      setResult(RESULT_OK);
      finish();
    }
  }

  private void renderErrors(ProviderUiState state) {
    binding.urlLayout.setError(null);
    binding.modelLayout.setError(null);
    binding.secretLayout.setError(null);
    binding.saveError.setVisibility(View.GONE);
    VibeConfigException.Problem problem = state.problem();
    if (problem != null) {
      switch (problem) {
        case MODEL_EMPTY:
          binding.modelLayout.setError(getString(R.string.vibe_error_model_empty));
          break;
        case MODEL_INVALID:
          binding.modelLayout.setError(getString(R.string.vibe_error_model_invalid));
          break;
        case BASE_URL_REQUIRED:
          binding.urlLayout.setError(getString(R.string.vibe_error_url_required));
          break;
        case BASE_URL_INVALID:
          binding.urlLayout.setError(getString(R.string.vibe_error_url_invalid));
          break;
        case BASE_URL_INSECURE:
          binding.urlLayout.setError(getString(R.string.vibe_error_url_insecure));
          break;
        case SECRET_MISSING:
          showSecretError(R.string.vibe_error_secret_missing);
          break;
        case SECRET_INVALID:
          showSecretError(R.string.vibe_error_secret_invalid);
          break;
        default:
          break;
      }
    }
    if (state.saveError() == ProviderUiState.SaveError.STORAGE_FAILED) {
      binding.saveError.setText(R.string.vibe_error_storage);
      binding.saveError.setVisibility(View.VISIBLE);
    }
  }

  private void showSecretError(@StringRes int message) {
    if (binding.secretLayout.getVisibility() != View.VISIBLE) {
      replacing = true;
      binding.secretLayout.setVisibility(View.VISIBLE);
      binding.secretSaved.setVisibility(View.GONE);
    }
    binding.secretLayout.setError(getString(message));
  }

  private void updateCleartextWarning() {
    if (lastState == null) {
      return;
    }
    boolean cleartext =
        ProviderForm.isCleartext(lastState.type(), String.valueOf(binding.urlInput.getText()));
    binding.cleartextWarning.setVisibility(cleartext ? View.VISIBLE : View.GONE);
  }

  private void renderAccount(ProviderUiState state) {
    ProviderUiState.LoginPhase phase = state.loginPhase();
    boolean installed = state.agentInstalled();
    boolean waiting =
        phase == ProviderUiState.LoginPhase.STARTING || phase == ProviderUiState.LoginPhase.CODE_READY;
    binding.accountCode.setVisibility(
        phase == ProviderUiState.LoginPhase.CODE_READY ? View.VISIBLE : View.GONE);
    binding.accountProgress.setVisibility(waiting ? View.VISIBLE : View.GONE);
    InstallProgressStyle.apply(binding.accountProgress, waiting);
    binding.accountCancel.setVisibility(waiting ? View.VISIBLE : View.GONE);
    binding.accountStart.setVisibility(waiting ? View.GONE : View.VISIBLE);
    binding.accountStart.setEnabled(installed);
    binding.accountStart.setText(
        phase == ProviderUiState.LoginPhase.FAILED
            ? R.string.vibe_account_retry
            : phase == ProviderUiState.LoginPhase.SUCCEEDED
                ? R.string.vibe_account_again
                : R.string.vibe_account_start);
    binding.accountUrl.setText(state.loginUrl());
    binding.accountUserCode.setText(state.loginCode());
    boolean fallback =
        phase == ProviderUiState.LoginPhase.FAILED
            && (state.loginFailure() == ChatGptAccountLogin.Failure.CODE_NOT_FOUND
                || state.loginFailure() == ChatGptAccountLogin.Failure.START_FAILED);
    binding.accountFallback.setVisibility(fallback ? View.VISIBLE : View.GONE);
    binding.accountCommand.setText(ChatGptAccountLogin.TERMINAL_COMMAND);
    binding.accountBody.setText(accountMessage(state, installed));
  }

  private String accountMessage(ProviderUiState state, boolean installed) {
    switch (state.loginPhase()) {
      case STARTING:
        return getString(R.string.vibe_account_starting);
      case CODE_READY:
        return getString(R.string.vibe_account_waiting);
      case SUCCEEDED:
        return getString(R.string.vibe_account_done);
      case FAILED:
        return failureMessage(state);
      default:
        return getString(installed ? R.string.vibe_account_body : R.string.vibe_account_not_installed);
    }
  }

  private String failureMessage(ProviderUiState state) {
    ChatGptAccountLogin.Failure failure = state.loginFailure();
    int message;
    if (failure == null) {
      message = R.string.vibe_account_failed_generic;
    } else {
      switch (failure) {
        case NOT_INSTALLED:
          message = R.string.vibe_account_not_installed;
          break;
        case START_FAILED:
          message = R.string.vibe_account_failed_start;
          break;
        case CODE_NOT_FOUND:
          message = R.string.vibe_account_failed_code;
          break;
        case REJECTED:
          message = R.string.vibe_account_failed_rejected;
          break;
        case TIMEOUT:
          message = R.string.vibe_account_failed_timeout;
          break;
        default:
          message = R.string.vibe_account_failed_generic;
          break;
      }
    }
    return getString(message);
  }

  private void onSaveClicked() {
    if (lastState == null) {
      return;
    }
    VibeProviderType type = lastState.type();
    String url = type == VibeProviderType.CHATGPT_ACCOUNT ? "" : text(binding.urlInput);
    String model = text(binding.modelInput);
    String secret = binding.secretLayout.getVisibility() == View.VISIBLE ? text(binding.secretInput) : "";
    if (ProviderForm.isCleartext(type, url) && !url.equals(cleartextConfirmedUrl)) {
      confirmCleartext(type, url, model, secret);
      return;
    }
    viewModel.save(url, model, secret);
  }

  private void confirmCleartext(VibeProviderType type, String url, String model, String secret) {
    new DialogCompat(this)
        .setTitle(getString(R.string.vibe_cleartext_title))
        .setMessage(getString(R.string.vibe_cleartext_message))
        .setPositiveButton(
            getString(R.string.vibe_cleartext_continue),
            (dialog, which) -> {
              cleartextConfirmedUrl = url;
              viewModel.save(url, model, secret);
            })
        .setNegativeButton(getString(R.string.vibe_action_cancel), null)
        .show();
  }

  private void confirmRemoveSecret() {
    new DialogCompat(this)
        .setTitle(getString(R.string.vibe_secret_remove_title))
        .setMessage(getString(R.string.vibe_secret_remove_message))
        .setPositiveButton(
            getString(R.string.vibe_secret_remove),
            (dialog, which) -> {
              replacing = false;
              viewModel.removeSecret();
            })
        .setNegativeButton(getString(R.string.vibe_action_cancel), null)
        .show();
  }

  private void openVerificationPage() {
    if (lastState == null || !ProviderForm.isSafeHttpsUrl(lastState.loginUrl())) {
      toast(R.string.vibe_account_url_rejected);
      return;
    }
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(lastState.loginUrl())));
    } catch (ActivityNotFoundException e) {
      toast(R.string.vibe_account_no_browser);
    }
  }

  private void openTerminal() {
    Intent intent = new Intent(this, TerminalActivity.class);
    intent.putExtra(TerminalActivity.EXTRA_COMMAND, ChatGptAccountLogin.TERMINAL_COMMAND);
    startActivity(intent);
  }

  private void copy(CharSequence text, boolean sensitive) {
    ClipboardManager clipboard = getSystemService(ClipboardManager.class);
    if (clipboard == null) {
      return;
    }
    ClipData clip = ClipData.newPlainText(getString(R.string.vibe_title), text);
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      PersistableBundle extras = new PersistableBundle();
      extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
      clip.getDescription().setExtras(extras);
    }
    clipboard.setPrimaryClip(clip);
    toast(R.string.vibe_copied);
  }

  private void toast(@StringRes int message) {
    GhostToast.makeText(this, getString(message), GhostToast.LENGTH_SHORT).show();
  }

  private static String text(TextInputEditText input) {
    Editable value = input.getText();
    return value == null ? "" : value.toString().trim();
  }

  private static final class TypeRow {
    final View root;
    final VibeProviderType type;
    final RadioButton radio;
    final TextView title;
    final TextView body;
    final GradientDrawable shape =
        new GradientDrawable();

    TypeRow(View root, VibeProviderType type, @StringRes int titleRes, @StringRes int bodyRes) {
      this.root = root;
      this.type = type;
      radio = root.findViewById(R.id.typeRadio);
      title = root.findViewById(R.id.typeTitle);
      body = root.findViewById(R.id.typeBody);
      title.setText(titleRes);
      body.setText(bodyRes);
      shape.setCornerRadius(VibePalette.dp(root.getContext(), CARD_RADIUS_DP));
      root.setBackground(shape);
    }

    void paint(@Nullable VibePalette palette, Context context, boolean selected) {
      if (palette == null) {
        return;
      }
      radio.setChecked(selected);
      radio.jumpDrawablesToCurrentState();
      shape.setColor(selected ? palette.containerHigh : palette.containerLow);
      shape.setStroke(
          selected ? VibePalette.dp(context, 1.5f) : 0, selected ? palette.primary : palette.containerLow);
      root.setSelected(selected);
      ViewCompat.setStateDescription(
          root,
          context.getString(selected ? R.string.vibe_state_selected : R.string.vibe_state_not_selected));
      root.setContentDescription(title.getText() + ". " + body.getText());
    }
  }

  private static final class SimpleWatcher implements TextWatcher {
    private final Runnable onChange;

    SimpleWatcher(Runnable onChange) {
      this.onChange = onChange;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
      onChange.run();
    }
  }
}

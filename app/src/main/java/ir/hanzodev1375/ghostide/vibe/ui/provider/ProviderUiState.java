package ir.hanzodev1375.ghostide.vibe.ui.provider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.ChatGptAccountLogin;
import ir.hanzodev1375.ghostide.vibe.VibeConfigException;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;

/** Immutable snapshot of the provider screen; contains no secret. */
public final class ProviderUiState {

  /** Progress of the ChatGPT account sign-in. */
  public enum LoginPhase {
    IDLE,
    /** Started; waiting for the code. */
    STARTING,
    /** The code is shown and the helper waits for the user. */
    CODE_READY,
    SUCCEEDED,
    FAILED
  }

  /** A saving problem that is not tied to one field. */
  public enum SaveError {
    NONE,
    /** The key could not be encrypted or written. */
    STORAGE_FAILED
  }

  private final VibeProviderType type;
  private final String baseUrl;
  private final String model;
  private final boolean loaded;
  private final boolean secretKnown;
  private final boolean hasSecret;
  private final boolean saving;
  private final boolean saved;
  private final VibeConfigException.Problem problem;
  private final SaveError saveError;
  private final boolean agentInstalled;
  private final LoginPhase loginPhase;
  private final String loginUrl;
  private final String loginCode;
  private final ChatGptAccountLogin.Failure loginFailure;
  private final String loginDetail;

  ProviderUiState(
      VibeProviderType type,
      String baseUrl,
      String model,
      boolean loaded,
      boolean secretKnown,
      boolean hasSecret,
      boolean saving,
      boolean saved,
      @Nullable VibeConfigException.Problem problem,
      SaveError saveError,
      boolean agentInstalled,
      LoginPhase loginPhase,
      String loginUrl,
      String loginCode,
      @Nullable ChatGptAccountLogin.Failure loginFailure,
      String loginDetail) {
    this.type = type;
    this.baseUrl = baseUrl;
    this.model = model;
    this.loaded = loaded;
    this.secretKnown = secretKnown;
    this.hasSecret = hasSecret;
    this.saving = saving;
    this.saved = saved;
    this.problem = problem;
    this.saveError = saveError;
    this.agentInstalled = agentInstalled;
    this.loginPhase = loginPhase;
    this.loginUrl = loginUrl;
    this.loginCode = loginCode;
    this.loginFailure = loginFailure;
    this.loginDetail = loginDetail;
  }

  @NonNull
  public VibeProviderType type() {
    return type;
  }

  /** The saved base URL, used to prefill the field once. */
  @NonNull
  public String baseUrl() {
    return baseUrl;
  }

  /** The saved model, used to prefill the field once. */
  @NonNull
  public String model() {
    return model;
  }

  /** False until the saved configuration has been read. */
  public boolean loaded() {
    return loaded;
  }

  /** True once it is known whether a key is stored for {@link #type()}. */
  public boolean secretKnown() {
    return secretKnown;
  }

  /** A key is stored for {@link #type()}; the key itself is never available. */
  public boolean hasSecret() {
    return hasSecret;
  }

  public boolean saving() {
    return saving;
  }

  /** True after a successful save; the screen then closes. */
  public boolean saved() {
    return saved;
  }

  /** The validation problem of the last save attempt, or {@code null}. */
  @Nullable
  public VibeConfigException.Problem problem() {
    return problem;
  }

  @NonNull
  public SaveError saveError() {
    return saveError;
  }

  /** Hermes is installed, so the account sign-in can run. */
  public boolean agentInstalled() {
    return agentInstalled;
  }

  @NonNull
  public LoginPhase loginPhase() {
    return loginPhase;
  }

  /** The https page to open while {@link #loginPhase()} is {@code CODE_READY}. */
  @NonNull
  public String loginUrl() {
    return loginUrl;
  }

  /** The code to type on that page. */
  @NonNull
  public String loginCode() {
    return loginCode;
  }

  @Nullable
  public ChatGptAccountLogin.Failure loginFailure() {
    return loginFailure;
  }

  /** Short diagnostic of the failed sign-in; may be empty. */
  @NonNull
  public String loginDetail() {
    return loginDetail;
  }
}

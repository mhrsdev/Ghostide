package ir.hanzodev1375.ghostide.vibe.ui.provider;

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
import ir.hanzodev1375.ghostide.vibe.ChatGptAccountLogin;
import ir.hanzodev1375.ghostide.vibe.VibeConfigException;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import ir.hanzodev1375.ghostide.vibe.ui.VibeGateway;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * State and actions of the provider screen: the saved configuration, the key-present flag, saving
 * and the ChatGPT account sign-in. It never keeps a key: the secret passes through {@link
 * #save} to the store and is not held. It survives rotation; an unfinished sign-in is cancelled in
 * {@link #onCleared()}. Public methods are main thread only.
 */
public final class VibeProviderViewModel extends ViewModel {

  private static final String TAG = "VibeProvider";

  private final VibeGateway gateway;
  private final ExecutorService io;
  private final Executor main;
  private final MutableLiveData<ProviderUiState> state = new MutableLiveData<>();
  private final ChatGptAccountLogin.Listener loginListener = new LoginCallbacks();

  private VibeProviderType type = VibeProviderType.ANTHROPIC;
  private String baseUrl = "";
  private String model = "";
  private boolean loaded;
  private boolean secretKnown;
  private boolean hasSecret;
  private boolean saving;
  private boolean saved;
  private boolean cleared;
  private VibeConfigException.Problem problem;
  private ProviderUiState.SaveError saveError = ProviderUiState.SaveError.NONE;
  private ProviderUiState.LoginPhase loginPhase = ProviderUiState.LoginPhase.IDLE;
  private String loginUrl = "";
  private String loginCode = "";
  private ChatGptAccountLogin.Failure loginFailure;
  private String loginDetail = "";
  private Runnable loginHandle;

  public VibeProviderViewModel(
      @NonNull VibeGateway gateway, @NonNull ExecutorService io, @NonNull Executor main) {
    this.gateway = gateway;
    this.io = io;
    this.main = main;
    publish();
    loadSaved();
  }

  /** Factory used by the screen. */
  public static final class Factory implements ViewModelProvider.Factory {
    private final Supplier<VibeGateway> gateway;

    public Factory(@NonNull Supplier<VibeGateway> gateway) {
      this.gateway = gateway;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      if (!modelClass.isAssignableFrom(VibeProviderViewModel.class)) {
        throw new IllegalArgumentException("Unknown view model " + modelClass.getName());
      }
      Handler handler = new Handler(Looper.getMainLooper());
      return (T)
          new VibeProviderViewModel(gateway.get(), Executors.newSingleThreadExecutor(), handler::post);
    }
  }

  @NonNull
  public LiveData<ProviderUiState> state() {
    return state;
  }

  /** Switches the selected provider type and looks up whether a key is stored for it. */
  @MainThread
  public void selectType(@NonNull VibeProviderType next) {
    if (next == type) {
      return;
    }
    type = next;
    problem = null;
    saveError = ProviderUiState.SaveError.NONE;
    secretKnown = !next.needsSecret();
    hasSecret = false;
    publish();
    if (next.needsSecret()) {
      lookUpSecret(next);
    }
  }

  /**
   * Validates and saves the provider.
   *
   * @param secret the key typed by the user, or empty to keep the stored one; not retained
   */
  @MainThread
  public void save(
      @NonNull String url, @NonNull String modelId, @Nullable String secret) {
    if (saving) {
      return;
    }
    baseUrl = url;
    model = modelId;
    problem = null;
    saveError = ProviderUiState.SaveError.NONE;
    VibeProviderConfig config;
    try {
      config = VibeProviderConfig.create(type, url, modelId);
    } catch (VibeConfigException e) {
      problem = e.getProblem();
      publish();
      return;
    }
    saving = true;
    publish();
    String key = secret == null || secret.isBlank() ? null : secret;
    runIo(
        () -> {
          VibeConfigException.Problem failed = null;
          ProviderUiState.SaveError storage = ProviderUiState.SaveError.NONE;
          try {
            gateway.saveProvider(config, key);
          } catch (VibeConfigException e) {
            failed = e.getProblem();
          } catch (IOException e) {
            Log.w(TAG, "provider could not be stored: " + e.getClass().getSimpleName());
            storage = ProviderUiState.SaveError.STORAGE_FAILED;
          }
          VibeConfigException.Problem finalProblem = failed;
          ProviderUiState.SaveError finalStorage = storage;
          main.execute(() -> onSaved(finalProblem, finalStorage));
        });
  }

  /** Removes the stored key of the selected provider. */
  @MainThread
  public void removeSecret() {
    VibeProviderType target = type;
    runIo(
        () -> {
          boolean removed = true;
          try {
            gateway.clearSecret(target);
          } catch (IOException e) {
            Log.w(TAG, "key could not be removed: " + e.getClass().getSimpleName());
            removed = false;
          }
          boolean finalRemoved = removed;
          main.execute(
              () -> {
                if (cleared || target != type) {
                  return;
                }
                if (finalRemoved) {
                  hasSecret = false;
                } else {
                  saveError = ProviderUiState.SaveError.STORAGE_FAILED;
                }
                publish();
              });
        });
  }

  /** Starts the ChatGPT account sign-in, replacing one that is running. */
  @MainThread
  public void beginLogin() {
    cancelHandle();
    loginPhase = ProviderUiState.LoginPhase.STARTING;
    loginUrl = "";
    loginCode = "";
    loginFailure = null;
    loginDetail = "";
    publish();
    loginHandle = gateway.beginChatGptLogin(loginListener);
  }

  /** Abandons the sign-in; no further result arrives. */
  @MainThread
  public void cancelLogin() {
    cancelHandle();
    loginPhase = ProviderUiState.LoginPhase.IDLE;
    publish();
  }

  @Override
  protected void onCleared() {
    cleared = true;
    cancelHandle();
    io.shutdown();
  }

  private void cancelHandle() {
    Runnable handle = loginHandle;
    loginHandle = null;
    if (handle != null) {
      handle.run();
    }
  }

  private void loadSaved() {
    runIo(
        () -> {
          VibeProviderConfig config = gateway.loadProvider();
          boolean secret =
              config != null && config.getType().needsSecret() && gateway.hasSecret(config.getType());
          main.execute(() -> onSavedLoaded(config, secret));
        });
  }

  private void onSavedLoaded(@Nullable VibeProviderConfig config, boolean secret) {
    if (cleared) {
      return;
    }
    if (config != null) {
      type = config.getType();
      baseUrl = config.getBaseUrl();
      model = config.getModel();
      hasSecret = secret;
    }
    secretKnown = config != null || !type.needsSecret();
    loaded = true;
    publish();
    if (!secretKnown) {
      lookUpSecret(type);
    }
  }

  private void lookUpSecret(VibeProviderType target) {
    runIo(
        () -> {
          boolean secret = gateway.hasSecret(target);
          main.execute(
              () -> {
                if (cleared || target != type) {
                  return;
                }
                hasSecret = secret;
                secretKnown = true;
                publish();
              });
        });
  }

  private void onSaved(
      @Nullable VibeConfigException.Problem failed, ProviderUiState.SaveError storage) {
    if (cleared) {
      return;
    }
    saving = false;
    problem = failed;
    saveError = storage;
    saved = failed == null && storage == ProviderUiState.SaveError.NONE;
    if (saved && type.needsSecret()) {
      hasSecret = true;
    }
    publish();
  }

  private void runIo(Runnable work) {
    try {
      io.execute(work);
    } catch (RejectedExecutionException e) {
      Log.w(TAG, "background work skipped: executor is closed");
    }
  }

  private void publish() {
    state.setValue(
        new ProviderUiState(
            type,
            baseUrl,
            model,
            loaded,
            secretKnown,
            hasSecret,
            saving,
            saved,
            problem,
            saveError,
            gateway.snapshot().isInstalled(),
            loginPhase,
            loginUrl,
            loginCode,
            loginFailure,
            loginDetail));
  }

  private final class LoginCallbacks implements ChatGptAccountLogin.Listener {

    @Override
    public void onCodeReady(@NonNull String verificationUrl, @NonNull String userCode) {
      if (cleared) {
        return;
      }
      loginPhase = ProviderUiState.LoginPhase.CODE_READY;
      loginUrl = verificationUrl;
      loginCode = userCode;
      publish();
    }

    @Override
    public void onSucceeded() {
      if (cleared) {
        return;
      }
      loginPhase = ProviderUiState.LoginPhase.SUCCEEDED;
      loginHandle = null;
      publish();
    }

    @Override
    public void onFailed(@NonNull ChatGptAccountLogin.Failure failure, @NonNull String detail) {
      if (cleared) {
        return;
      }
      loginPhase = ProviderUiState.LoginPhase.FAILED;
      loginFailure = failure;
      loginDetail = detail;
      loginHandle = null;
      publish();
    }
  }
}

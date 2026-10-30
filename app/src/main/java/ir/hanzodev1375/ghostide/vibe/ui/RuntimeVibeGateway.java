package ir.hanzodev1375.ghostide.vibe.ui;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.ChatGptAccountLogin;
import ir.hanzodev1375.ghostide.vibe.HermesApiClient;
import ir.hanzodev1375.ghostide.vibe.HermesRunSession;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;
import ir.hanzodev1375.ghostide.vibe.VibeConfigException;
import ir.hanzodev1375.ghostide.vibe.VibeEvent;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeProviderStore;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import ir.hanzodev1375.ghostide.vibe.VibeTurn;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Production {@link VibeGateway}: forwards to the application-wide {@link HermesRuntime} and
 * {@link VibeProviderStore}. Only the application context is retained.
 */
public final class RuntimeVibeGateway implements VibeGateway {

  private final HermesRuntime runtime;
  private final VibeProviderStore store;

  public RuntimeVibeGateway(@NonNull Context context) {
    Context app = context.getApplicationContext();
    this.runtime = HermesRuntime.get(app);
    this.store = VibeProviderStore.get(app);
  }

  @NonNull
  @Override
  public HermesRuntime.Snapshot snapshot() {
    return runtime.getSnapshot();
  }

  @Override
  public void addRuntimeListener(@NonNull HermesRuntime.Listener listener) {
    runtime.addListener(listener);
  }

  @Override
  public void removeRuntimeListener(@NonNull HermesRuntime.Listener listener) {
    runtime.removeListener(listener);
  }

  @Override
  public boolean isRootfsInstalled() {
    return runtime.isRootfsInstalled();
  }

  @Override
  public boolean install() {
    return runtime.install();
  }

  @Override
  public void cancelInstall() {
    runtime.cancelInstall();
  }

  @Override
  public boolean start(@NonNull File projectFolder) {
    return runtime.start(projectFolder);
  }

  @Override
  public void stop() {
    runtime.stop();
  }

  @NonNull
  @Override
  public Run startRun(
      @NonNull String prompt,
      @Nullable String sessionId,
      @Nullable List<VibeTurn> history,
      @NonNull HermesRunSession.Listener listener) {
    HermesRunSession session =
        runtime.startRun(prompt, sessionId, HermesRuntime.DEFAULT_INSTRUCTIONS, history, listener);
    return new Run() {
      @Override
      public void approve(
          @NonNull VibeEvent.ApprovalRequested request,
          @NonNull HermesApiClient.Decision decision) {
        session.approve(request, decision);
      }

      @Override
      public void stop() {
        session.stop();
      }

      @Override
      public void close() {
        session.close();
      }
    };
  }

  @Nullable
  @Override
  public VibeProviderConfig loadProvider() {
    return store.load();
  }

  @Override
  public boolean hasSecret(@NonNull VibeProviderType type) {
    return store.hasSecret(type);
  }

  @Override
  public void saveProvider(@NonNull VibeProviderConfig config, @Nullable String secret)
      throws VibeConfigException, IOException {
    store.save(config, secret);
  }

  @Override
  public void clearSecret(@NonNull VibeProviderType type) throws IOException {
    store.clearSecret(type);
  }

  @NonNull
  @Override
  public Runnable beginChatGptLogin(@NonNull ChatGptAccountLogin.Listener listener) {
    ChatGptAccountLogin login = runtime.beginChatGptLogin(listener);
    return login::cancel;
  }
}

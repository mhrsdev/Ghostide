package ir.hanzodev1375.ghostide.vibe.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.ChatGptAccountLogin;
import ir.hanzodev1375.ghostide.vibe.HermesApiClient;
import ir.hanzodev1375.ghostide.vibe.HermesRunSession;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;
import ir.hanzodev1375.ghostide.vibe.VibeConfigException;
import ir.hanzodev1375.ghostide.vibe.VibeEvent;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.VibeProviderType;
import ir.hanzodev1375.ghostide.vibe.VibeTurn;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Everything the Vibe Coding screens need from the backend, as one small interface.
 *
 * <p>The backend entry points ({@code HermesRuntime}, {@code HermesRunSession}, {@code
 * VibeProviderStore}) are final singletons with package-private constructors, so a view model that
 * used them directly could not be driven by anything but a real proot server. This interface is the
 * only seam: {@link RuntimeVibeGateway} delegates to the real classes and tests substitute a double
 * that replays synthetic {@link VibeEvent}s. Methods documented as blocking must not be called on
 * the main thread.
 */
public interface VibeGateway {

  /** One agent run; a thin handle over {@code HermesRunSession}. */
  interface Run {
    /** Answers the pending approval {@code request}. */
    void approve(@NonNull VibeEvent.ApprovalRequested request, @NonNull HermesApiClient.Decision decision);

    /** Asks the agent to stop. */
    void stop();

    /** Stops listening; does not stop the run. */
    void close();
  }

  /** The current runtime state. */
  @NonNull
  HermesRuntime.Snapshot snapshot();

  /** Registers {@code listener}; it receives the current state at once, on the main thread. */
  void addRuntimeListener(@NonNull HermesRuntime.Listener listener);

  void removeRuntimeListener(@NonNull HermesRuntime.Listener listener);

  /** True if the Debian rootfs that Hermes needs is installed. */
  boolean isRootfsInstalled();

  /** Starts the install; false if nothing was started. */
  boolean install();

  void cancelInstall();

  /** Starts the server for {@code projectFolder}; false if Hermes is not installed or busy. */
  boolean start(@NonNull File projectFolder);

  void stop();

  /**
   * Starts an agent run.
   *
   * @throws IllegalStateException if the server is not running
   */
  @NonNull
  Run startRun(
      @NonNull String prompt,
      @Nullable String sessionId,
      @Nullable List<VibeTurn> history,
      @NonNull HermesRunSession.Listener listener);

  /** The saved provider configuration, or {@code null}. Reads preferences only. */
  @Nullable
  VibeProviderConfig loadProvider();

  /** True if an API key is stored for {@code type}. Blocking. */
  boolean hasSecret(@NonNull VibeProviderType type);

  /**
   * Validates and saves the provider and, if given, its key. Blocking.
   *
   * @throws VibeConfigException if the key is unusable
   * @throws IOException if the key cannot be stored
   */
  void saveProvider(@NonNull VibeProviderConfig config, @Nullable String secret)
      throws VibeConfigException, IOException;

  /** Removes the stored key of {@code type}. Blocking. */
  void clearSecret(@NonNull VibeProviderType type) throws IOException;

  /**
   * Starts the ChatGPT account sign-in.
   *
   * @return a handle that cancels the sign-in
   */
  @NonNull
  Runnable beginChatGptLogin(@NonNull ChatGptAccountLogin.Listener listener);
}

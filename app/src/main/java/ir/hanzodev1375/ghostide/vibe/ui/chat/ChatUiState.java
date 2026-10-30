package ir.hanzodev1375.ghostide.vibe.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.HermesRuntime;
import ir.hanzodev1375.ghostide.vibe.VibeProviderConfig;
import ir.hanzodev1375.ghostide.vibe.ui.setup.SetupModel;
import java.util.List;

/** Immutable snapshot of everything the chat screen draws. */
public final class ChatUiState {

  /** A short, one-time message for the user; the screen maps {@link Kind} to localized text. */
  public record UiMessage(int serial, Kind kind) {

    /** What happened. */
    public enum Kind {
      OFFLINE,
      APPROVAL_FAILED,
      STOP_FAILED,
      AGENT_NOT_RUNNING,
      PROVIDER_CHANGED_BUSY
    }
  }

  private final List<ChatItem> items;
  private final SetupModel setup;
  private final HermesRuntime.Snapshot snapshot;
  private final VibeProviderConfig provider;
  private final boolean loaded;
  private final boolean running;
  private final boolean stopping;
  private final boolean online;
  private final boolean showOfflineNotice;
  private final String projectName;
  private final UiMessage message;

  ChatUiState(
      List<ChatItem> items,
      SetupModel setup,
      HermesRuntime.Snapshot snapshot,
      @Nullable VibeProviderConfig provider,
      boolean loaded,
      boolean running,
      boolean stopping,
      boolean online,
      boolean showOfflineNotice,
      String projectName,
      @Nullable UiMessage message) {
    this.items = items;
    this.setup = setup;
    this.snapshot = snapshot;
    this.provider = provider;
    this.loaded = loaded;
    this.running = running;
    this.stopping = stopping;
    this.online = online;
    this.showOfflineNotice = showOfflineNotice;
    this.projectName = projectName;
    this.message = message;
  }

  @NonNull
  public List<ChatItem> items() {
    return items;
  }

  @NonNull
  public SetupModel setup() {
    return setup;
  }

  @NonNull
  public HermesRuntime.Snapshot snapshot() {
    return snapshot;
  }

  /** The saved provider, or {@code null}. */
  @Nullable
  public VibeProviderConfig provider() {
    return provider;
  }

  /** False until the saved transcript and provider have been read. */
  public boolean loaded() {
    return loaded;
  }

  /** A run is active (started and not yet finished). */
  public boolean running() {
    return running;
  }

  /** The user asked the active run to stop and it has not ended yet. */
  public boolean stopping() {
    return stopping;
  }

  public boolean online() {
    return online;
  }

  /** True when the device is offline and the chosen provider needs internet. */
  public boolean showOfflineNotice() {
    return showOfflineNotice;
  }

  /** Folder name of the project, for the title line. */
  @NonNull
  public String projectName() {
    return projectName;
  }

  @Nullable
  public UiMessage message() {
    return message;
  }

  /** True if a prompt can be sent right now. */
  public boolean canSend() {
    return loaded && !running && setup.phase() == SetupModel.Phase.READY;
  }
}

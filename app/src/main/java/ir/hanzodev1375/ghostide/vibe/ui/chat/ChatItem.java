package ir.hanzodev1375.ghostide.vibe.ui.chat;

import androidx.annotation.Nullable;
import ir.hanzodev1375.ghostide.vibe.VibeEvent;
import java.util.List;

/**
 * One row of the Vibe Coding transcript. Items are immutable value objects with a stable {@link
 * #id()}, so a {@code DiffUtil} callback can compare ids for identity and {@code equals} for
 * content. {@link VibeTranscript} replaces an item with a modified copy instead of mutating it.
 */
public sealed interface ChatItem {

  /** Stable identity of the row inside one transcript; never reused. */
  long id();

  /** Progress of a tool call. */
  enum ToolState {
    RUNNING,
    DONE,
    FAILED,
    /** The run ended before the tool reported a result. */
    UNKNOWN
  }

  /** Where an approval request stands. */
  enum ApprovalStatus {
    /** Waiting for the user. */
    PENDING,
    /** The user answered and the answer is on its way to the agent. */
    SUBMITTING,
    ALLOWED,
    DENIED,
    /** The agent was told another answer elsewhere (settled by a different client or by Hermes). */
    RESOLVED_ELSEWHERE,
    /** The run ended or the app restarted before the request was answered. */
    EXPIRED
  }

  /** The kind of a one-line status row. */
  enum NoticeKind {
    SUBAGENT_STARTED,
    SUBAGENT_FINISHED,
    QUEUED,
    STEERED,
    REPLAY_TRUNCATED,
    STOPPED,
    INTERRUPTED,
    /** The previous run was lost because the app was closed or killed. */
    ABANDONED
  }

  /** Why a run could not be completed. */
  enum FailureKind {
    /** The agent reported an error while running (provider rejected the key, tool crashed...). */
    RUN_FAILED,
    /** The connection to the local agent broke and could not be restored. */
    CONNECTION_LOST,
    /** The run could not be created. */
    START_FAILED
  }

  /** What the user typed. */
  record UserMessage(long id, String text) implements ChatItem {}

  /**
   * A piece of the assistant's answer. {@code streaming} is true while chunks may still be added.
   */
  record AssistantMessage(long id, String text, boolean streaming) implements ChatItem {}

  /** A tool the agent ran; {@code preview} is a short redacted description from Hermes. */
  record ToolCall(
      long id, String callId, String tool, String preview, ToolState state, double seconds)
      implements ChatItem {}

  /**
   * A request to run something that needs the user's decision. {@code request} is {@code null}
   * for an item restored from disk, which can no longer be answered.
   */
  record Approval(
      long id,
      @Nullable VibeEvent.ApprovalRequested request,
      String promptId,
      String command,
      String description,
      List<String> choices,
      ApprovalStatus status,
      String choice)
      implements ChatItem {

    /** True while the user can still answer. */
    public boolean isOpen() {
      return status == ApprovalStatus.PENDING || status == ApprovalStatus.SUBMITTING;
    }
  }

  /** A one-line status row; {@code text} is optional detail such as a sub-agent description. */
  record Notice(long id, NoticeKind kind, String text) implements ChatItem {}

  /** Token counters of a finished run. */
  record Usage(long id, long inputTokens, long outputTokens, long totalTokens)
      implements ChatItem {}

  /**
   * A failed run. {@code apiKind} is the name of the {@code HermesApiException.Kind} for
   * {@link FailureKind#START_FAILED} and {@link FailureKind#CONNECTION_LOST}, otherwise empty;
   * {@code detail} is the redacted diagnostic from Hermes.
   */
  record Failure(long id, FailureKind kind, String apiKind, String detail) implements ChatItem {}
}

package ir.hanzodev1375.ghostide.vibe;

import java.util.List;

/**
 * Immutable events of one agent run, decoded from the Hermes {@code /v1/runs/{id}/events} stream.
 *
 * <p>Every event knows its run and its sequence number ({@link #seq()}; -1 when the Hermes build
 * does not number events). Text fields are never null. Render with {@code instanceof} patterns;
 * unknown future event types arrive as {@link Unknown} and can be ignored safely.
 */
public sealed interface VibeEvent {

  /** The run this event belongs to. */
  String runId();

  /** Server sequence number, or -1 if the server did not number the event. */
  long seq();

  /** True for the four events after which no further event of the run arrives. */
  default boolean isTerminal() {
    return this instanceof RunCompleted
        || this instanceof RunFailed
        || this instanceof RunCancelled
        || this instanceof RunInterrupted;
  }

  /** A chunk of the assistant's answer text. Consecutive chunks may be merged by the session. */
  record MessageDelta(String runId, long seq, String text) implements VibeEvent {}

  /** Commentary the model wrote between tool calls. */
  record MessageInterim(String runId, long seq, String text, boolean alreadyStreamed)
      implements VibeEvent {}

  /** Reasoning or a full-text summary of the step (Hermes 0.19 repeats the answer here). */
  record Reasoning(String runId, long seq, String text) implements VibeEvent {}

  /** A tool is about to run; {@code preview} is a short redacted description of its arguments. */
  record ToolStarted(String runId, long seq, String toolCallId, String tool, String preview)
      implements VibeEvent {}

  /** A tool finished; {@code error} is true when it reported a failure. */
  record ToolCompleted(
      String runId,
      long seq,
      String toolCallId,
      String tool,
      double durationSeconds,
      boolean error,
      String preview)
      implements VibeEvent {}

  /**
   * The agent wants to run something that needs the user's decision. The run stays parked until
   * {@code HermesRunSession.approve} is called or Hermes' approval timeout (10 minutes here) denies
   * it. {@code promptId} is empty on Hermes builds that do not identify prompts.
   */
  record ApprovalRequested(
      String runId,
      long seq,
      String promptId,
      long executionGeneration,
      String command,
      String description,
      List<String> choices)
      implements VibeEvent {}

  /** The pending approval was settled (by this client or elsewhere). */
  record ApprovalResolved(String runId, long seq, String promptId, String choice)
      implements VibeEvent {}

  /** Steering text was accepted by the running agent. */
  record RunSteered(String runId, long seq, boolean accepted) implements VibeEvent {}

  /** A delegated sub-agent started ({@code phase} is "start") or finished ("complete"). */
  record Subagent(String runId, long seq, String phase, String preview) implements VibeEvent {}

  /** The run is queued behind another turn of the same session. */
  record RunQueued(String runId, long seq) implements VibeEvent {}

  /** The server dropped older events before the requested position; some output was lost. */
  record ReplayTruncated(String runId, long seq, long oldestRetainedSeq) implements VibeEvent {}

  /** Terminal: the agent finished; {@code output} is the final answer. */
  record RunCompleted(String runId, long seq, String output, VibeUsage usage)
      implements VibeEvent {}

  /** Terminal: the run failed; {@code error} is a redacted human-readable reason. */
  record RunFailed(String runId, long seq, String error) implements VibeEvent {}

  /** Terminal: the run was stopped by the user. */
  record RunCancelled(String runId, long seq) implements VibeEvent {}

  /** Terminal: the Hermes server shut down while the run was active. */
  record RunInterrupted(String runId, long seq, String reason) implements VibeEvent {}

  /** An event type this client does not know; {@code rawJson} is the original payload. */
  record Unknown(String runId, long seq, String type, String rawJson) implements VibeEvent {}

  /** A frame whose payload was not a JSON object; kept so the stream is never silently lossy. */
  record Malformed(String runId, long seq, String reason) implements VibeEvent {}
}

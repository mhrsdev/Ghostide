package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns one SSE frame of the Hermes run stream into a {@link VibeEvent}. Tolerant by design: a
 * payload that is not a JSON object becomes {@link VibeEvent.Malformed}, an unknown {@code event}
 * becomes {@link VibeEvent.Unknown}, missing fields get neutral defaults. Never throws.
 */
final class VibeEventDecoder {

  private static final long NO_SEQ = -1;

  private VibeEventDecoder() {}

  /**
   * Decodes a frame.
   *
   * @param runId run id to use when the payload carries none
   * @param frameId the SSE {@code id:} of the frame, used as sequence number when the payload has
   *     no {@code seq}; may be null
   * @param data the SSE data payload
   */
  @NonNull
  static VibeEvent decode(@NonNull String runId, String frameId, @NonNull String data) {
    JsonObject json;
    try {
      JsonElement parsed = JsonParser.parseString(data);
      if (!parsed.isJsonObject()) {
        return new VibeEvent.Malformed(runId, parseSeq(frameId), "payload is not a JSON object");
      }
      json = parsed.getAsJsonObject();
    } catch (RuntimeException e) {
      return new VibeEvent.Malformed(runId, parseSeq(frameId), "payload is not valid JSON");
    }
    String run = text(json, "run_id", runId);
    long seq = json.has("seq") ? number(json, "seq", NO_SEQ) : parseSeq(frameId);
    String type = text(json, "event", "");
    switch (type) {
      case "message.delta":
        return new VibeEvent.MessageDelta(run, seq, text(json, "delta", ""));
      case "message.interim":
        return new VibeEvent.MessageInterim(
            run, seq, text(json, "text", ""), bool(json, "already_streamed", false));
      case "reasoning.available":
        return new VibeEvent.Reasoning(run, seq, text(json, "text", ""));
      case "tool.started":
        return new VibeEvent.ToolStarted(
            run, seq, text(json, "tool_call_id", ""), text(json, "tool", ""), text(json, "preview", ""));
      case "tool.completed":
      case "tool.failed":
        return new VibeEvent.ToolCompleted(
            run,
            seq,
            text(json, "tool_call_id", ""),
            text(json, "tool", ""),
            decimal(json, "duration", 0),
            "tool.failed".equals(type) || bool(json, "error", false),
            text(json, "preview", ""));
      case "approval.request":
        return new VibeEvent.ApprovalRequested(
            run,
            seq,
            text(json, "prompt_id", text(json, "request_id", "")),
            number(json, "execution_generation", NO_SEQ),
            text(json, "command", ""),
            text(json, "description", ""),
            strings(json, "choices"));
      case "approval.responded":
      case "approval.settled":
        return new VibeEvent.ApprovalResolved(
            run, seq, text(json, "prompt_id", text(json, "request_id", "")), text(json, "choice", ""));
      case "run.steered":
        return new VibeEvent.RunSteered(run, seq, bool(json, "accepted", true));
      case "subagent.start":
        return new VibeEvent.Subagent(run, seq, "start", text(json, "preview", ""));
      case "subagent.complete":
        return new VibeEvent.Subagent(run, seq, "complete", text(json, "preview", ""));
      case "run.queued":
        return new VibeEvent.RunQueued(run, seq);
      case "replay.truncated":
        return new VibeEvent.ReplayTruncated(run, seq, number(json, "oldest_retained_seq", NO_SEQ));
      case "run.completed":
        return new VibeEvent.RunCompleted(run, seq, text(json, "output", ""), usage(json));
      case "run.failed":
        return new VibeEvent.RunFailed(run, seq, text(json, "error", ""));
      case "run.cancelled":
        return new VibeEvent.RunCancelled(run, seq);
      case "run.interrupted":
        return new VibeEvent.RunInterrupted(run, seq, text(json, "error", ""));
      default:
        return new VibeEvent.Unknown(run, seq, type, data);
    }
  }

  /** Reads the {@code usage} object of a run status or terminal event. */
  @NonNull
  static VibeUsage usage(@NonNull JsonObject owner) {
    JsonElement element = owner.get("usage");
    if (element == null || !element.isJsonObject()) {
      return VibeUsage.NONE;
    }
    JsonObject usage = element.getAsJsonObject();
    return new VibeUsage(
        number(usage, "input_tokens", 0),
        number(usage, "output_tokens", 0),
        number(usage, "total_tokens", 0),
        number(usage, "cache_read_tokens", 0),
        number(usage, "cache_write_tokens", 0));
  }

  static String text(JsonObject json, String key, String fallback) {
    JsonElement element = json.get(key);
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }
    return element.getAsString();
  }

  static long number(JsonObject json, String key, long fallback) {
    JsonElement element = json.get(key);
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }
    try {
      return element.getAsLong();
    } catch (NumberFormatException | UnsupportedOperationException e) {
      return fallback;
    }
  }

  private static double decimal(JsonObject json, String key, double fallback) {
    JsonElement element = json.get(key);
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }
    try {
      return element.getAsDouble();
    } catch (NumberFormatException | UnsupportedOperationException e) {
      return fallback;
    }
  }

  private static boolean bool(JsonObject json, String key, boolean fallback) {
    JsonElement element = json.get(key);
    if (element == null || !element.isJsonPrimitive()) {
      return fallback;
    }
    if (element.getAsJsonPrimitive().isBoolean()) {
      return element.getAsBoolean();
    }
    return fallback;
  }

  private static List<String> strings(JsonObject json, String key) {
    JsonElement element = json.get(key);
    if (element == null || !element.isJsonArray()) {
      return Collections.emptyList();
    }
    JsonArray array = element.getAsJsonArray();
    List<String> result = new ArrayList<>(array.size());
    for (JsonElement item : array) {
      if (item.isJsonPrimitive()) {
        result.add(item.getAsString());
      }
    }
    return Collections.unmodifiableList(result);
  }

  private static long parseSeq(String frameId) {
    if (frameId == null) {
      return NO_SEQ;
    }
    try {
      return Long.parseLong(frameId.trim());
    } catch (NumberFormatException e) {
      return NO_SEQ;
    }
  }
}

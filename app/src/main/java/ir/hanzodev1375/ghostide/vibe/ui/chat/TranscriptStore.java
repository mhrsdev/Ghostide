package ir.hanzodev1375.ghostide.vibe.ui.chat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps one transcript per project folder in an app-private directory.
 *
 * <p>The file holds only what the chat shows (messages, tool previews, approval text, usage) and
 * never a provider key or the local agent key. It is bounded ({@link #MAX_FILE_BYTES}; the oldest
 * rows are dropped to fit), written atomically (temporary file plus rename, so a crash leaves
 * either the old or the new file) and read tolerantly: a missing, oversized, truncated or
 * corrupt file, or a single damaged row, yields what could be read and never an exception.
 *
 * <p>Methods do file I/O; call them off the main thread.
 */
public final class TranscriptStore {

  /** Largest file written or accepted. */
  public static final int MAX_FILE_BYTES = 1_500_000;

  private static final int FORMAT_VERSION = 1;
  private static final int DIGEST_HEX_CHARS = 32;
  private static final String FILE_SUFFIX = ".json";

  private final File directory;

  /** What {@link #load} found. */
  public static final class Stored {
    private final String sessionId;
    private final List<ChatItem> items;
    private final boolean runWasOpen;

    Stored(String sessionId, List<ChatItem> items, boolean runWasOpen) {
      this.sessionId = sessionId;
      this.items = items;
      this.runWasOpen = runWasOpen;
    }

    /** The conversation key, never empty. */
    @NonNull
    public String sessionId() {
      return sessionId;
    }

    @NonNull
    public List<ChatItem> items() {
      return items;
    }

    /** True if a run was still active when the file was written (the app closed during it). */
    public boolean runWasOpen() {
      return runWasOpen;
    }
  }

  /** @param directory the folder that holds the transcript files; created on first save */
  public TranscriptStore(@NonNull File directory) {
    this.directory = directory;
  }

  /** The file that holds the transcript of {@code project}. */
  @NonNull
  public File fileFor(@NonNull File project) {
    return new File(directory, digest(project.getPath()) + FILE_SUFFIX);
  }

  /** Reads the transcript of {@code project}, or returns {@code null} if there is none usable. */
  @Nullable
  public Stored load(@NonNull File project) {
    File file = fileFor(project);
    if (!file.isFile() || file.length() > MAX_FILE_BYTES) {
      return null;
    }
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      JsonElement root = JsonParser.parseReader(reader);
      if (!root.isJsonObject()) {
        return null;
      }
      return parse(root.getAsJsonObject());
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /**
   * Writes the transcript of {@code project}.
   *
   * @param runOpen whether a run is active, so the next load can report that it was cut short
   * @throws IOException if the file cannot be written; the previous file is left untouched
   */
  public void save(
      @NonNull File project,
      @NonNull String sessionId,
      @NonNull List<ChatItem> items,
      boolean runOpen)
      throws IOException {
    List<ChatItem> kept = new ArrayList<>(items);
    byte[] bytes = serialize(sessionId, kept, runOpen);
    while (bytes.length > MAX_FILE_BYTES && !kept.isEmpty()) {
      kept.subList(0, Math.max(1, kept.size() / 4)).clear();
      bytes = serialize(sessionId, kept, runOpen);
    }
    Files.createDirectories(directory.toPath());
    Path target = fileFor(project).toPath();
    Path temp = Files.createTempFile(directory.toPath(), "chat", ".tmp");
    try {
      Files.write(temp, bytes);
      try {
        Files.move(
            temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** Removes the saved transcript of {@code project}, if any. */
  public void delete(@NonNull File project) throws IOException {
    Files.deleteIfExists(fileFor(project).toPath());
  }

  private static byte[] serialize(String sessionId, List<ChatItem> items, boolean runOpen) {
    JsonObject root = new JsonObject();
    root.addProperty("v", FORMAT_VERSION);
    root.addProperty("session", sessionId);
    root.addProperty("runOpen", runOpen);
    JsonArray array = new JsonArray();
    for (ChatItem item : items) {
      JsonObject json = toJson(item);
      if (json != null) {
        array.add(json);
      }
    }
    root.add("items", array);
    return root.toString().getBytes(StandardCharsets.UTF_8);
  }

  @Nullable
  private static JsonObject toJson(ChatItem item) {
    JsonObject json = new JsonObject();
    json.addProperty("id", item.id());
    if (item instanceof ChatItem.UserMessage user) {
      json.addProperty("t", "user");
      json.addProperty("text", user.text());
    } else if (item instanceof ChatItem.AssistantMessage message) {
      json.addProperty("t", "asst");
      json.addProperty("text", message.text());
    } else if (item instanceof ChatItem.ToolCall call) {
      json.addProperty("t", "tool");
      json.addProperty("call", call.callId());
      json.addProperty("tool", call.tool());
      json.addProperty("preview", call.preview());
      ChatItem.ToolState state =
          call.state() == ChatItem.ToolState.RUNNING ? ChatItem.ToolState.UNKNOWN : call.state();
      json.addProperty("state", state.name());
      json.addProperty("sec", call.seconds());
    } else if (item instanceof ChatItem.Approval approval) {
      json.addProperty("t", "appr");
      json.addProperty("prompt", approval.promptId());
      json.addProperty("cmd", approval.command());
      json.addProperty("desc", approval.description());
      JsonArray choices = new JsonArray();
      approval.choices().forEach(choices::add);
      json.add("choices", choices);
      ChatItem.ApprovalStatus status =
          approval.isOpen() ? ChatItem.ApprovalStatus.EXPIRED : approval.status();
      json.addProperty("status", status.name());
      json.addProperty("choice", approval.choice());
    } else if (item instanceof ChatItem.Notice notice) {
      json.addProperty("t", "note");
      json.addProperty("kind", notice.kind().name());
      json.addProperty("text", notice.text());
    } else if (item instanceof ChatItem.Usage usage) {
      json.addProperty("t", "use");
      json.addProperty("in", usage.inputTokens());
      json.addProperty("out", usage.outputTokens());
      json.addProperty("total", usage.totalTokens());
    } else if (item instanceof ChatItem.Failure failure) {
      json.addProperty("t", "fail");
      json.addProperty("kind", failure.kind().name());
      json.addProperty("api", failure.apiKind());
      json.addProperty("detail", failure.detail());
    } else {
      return null;
    }
    return json;
  }

  @Nullable
  private static Stored parse(JsonObject root) {
    String session = string(root, "session", "");
    JsonElement array = root.get("items");
    if (session.isEmpty() || array == null || !array.isJsonArray()) {
      return null;
    }
    List<ChatItem> items = new ArrayList<>();
    for (JsonElement element : array.getAsJsonArray()) {
      if (!element.isJsonObject()) {
        continue;
      }
      ChatItem item = fromJson(element.getAsJsonObject());
      if (item != null) {
        items.add(item);
      }
    }
    boolean open = root.has("runOpen") && root.get("runOpen").getAsBoolean();
    return new Stored(session, items, open);
  }

  @Nullable
  private static ChatItem fromJson(JsonObject json) {
    try {
      long id = json.get("id").getAsLong();
      switch (string(json, "t", "")) {
        case "user":
          return new ChatItem.UserMessage(id, string(json, "text", ""));
        case "asst":
          return new ChatItem.AssistantMessage(id, string(json, "text", ""), false);
        case "tool":
          return new ChatItem.ToolCall(
              id,
              string(json, "call", ""),
              string(json, "tool", ""),
              string(json, "preview", ""),
              ChatItem.ToolState.valueOf(string(json, "state", "UNKNOWN")),
              json.has("sec") ? json.get("sec").getAsDouble() : 0);
        case "appr":
          return new ChatItem.Approval(
              id,
              null,
              string(json, "prompt", ""),
              string(json, "cmd", ""),
              string(json, "desc", ""),
              strings(json.get("choices")),
              ChatItem.ApprovalStatus.valueOf(string(json, "status", "EXPIRED")),
              string(json, "choice", ""));
        case "note":
          return new ChatItem.Notice(
              id,
              ChatItem.NoticeKind.valueOf(string(json, "kind", "")),
              string(json, "text", ""));
        case "use":
          return new ChatItem.Usage(
              id,
              json.get("in").getAsLong(),
              json.get("out").getAsLong(),
              json.get("total").getAsLong());
        case "fail":
          return new ChatItem.Failure(
              id,
              ChatItem.FailureKind.valueOf(string(json, "kind", "")),
              string(json, "api", ""),
              string(json, "detail", ""));
        default:
          return null;
      }
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static List<String> strings(@Nullable JsonElement element) {
    List<String> values = new ArrayList<>();
    if (element != null && element.isJsonArray()) {
      for (JsonElement entry : element.getAsJsonArray()) {
        if (entry.isJsonPrimitive()) {
          values.add(entry.getAsString());
        }
      }
    }
    return List.copyOf(values);
  }

  private static String string(JsonObject json, String key, String fallback) {
    JsonElement element = json.get(key);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
  }

  private static String digest(String value) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return hex.substring(0, DIGEST_HEX_CHARS);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is a required JDK algorithm", e);
    }
  }
}

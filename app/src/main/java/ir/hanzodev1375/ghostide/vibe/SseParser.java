package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Incremental Server-Sent-Events parser following the WHATWG rules: lines end with LF, CR or CRLF
 * (also when split across reads), a blank line dispatches the pending event, {@code :} lines are
 * comments, several {@code data:} lines are joined with LF, one leading space of a value is
 * dropped, a leading UTF-8 BOM is ignored.
 *
 * <p>Bytes are split into lines before they are decoded, so a multi-byte character cut by a read
 * boundary is decoded correctly. An event that is still incomplete at end of stream is discarded,
 * as the specification requires; {@link Result#droppedIncompleteEvent} tells the caller. A single
 * line longer than {@link #MAX_LINE_BYTES} aborts parsing with an {@link IOException}.
 */
final class SseParser {

  /** Upper bound of one line; a run's final answer is sent as one JSON line. */
  static final int MAX_LINE_BYTES = 4 * 1024 * 1024;

  private static final int READ_BUFFER_BYTES = 8192;
  private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  /** Receives parsed frames; called on the thread that runs {@link #parse}. */
  interface Listener {
    /**
     * A complete event.
     *
     * @param id the last {@code id:} seen (sticky across events as in the specification), or null
     * @param event the {@code event:} name, or "message" when absent
     * @param data the joined data lines without the final LF
     * @return false to stop reading
     */
    boolean onEvent(@Nullable String id, @NonNull String event, @NonNull String data);

    /**
     * A comment line (keepalive); {@code text} excludes the leading colon.
     *
     * @return false to stop reading
     */
    default boolean onComment(@NonNull String text) {
      return true;
    }
  }

  /** Why and how parsing ended. */
  static final class Result {
    /** True if the listener asked to stop; false if the stream ended. */
    final boolean stoppedByListener;
    /** True if the stream ended inside an event that was never completed by a blank line. */
    final boolean droppedIncompleteEvent;
    /** The last event id seen, or null. */
    @Nullable final String lastEventId;

    Result(boolean stoppedByListener, boolean droppedIncompleteEvent, @Nullable String lastEventId) {
      this.stoppedByListener = stoppedByListener;
      this.droppedIncompleteEvent = droppedIncompleteEvent;
      this.lastEventId = lastEventId;
    }
  }

  private final Listener listener;
  private final StringBuilder data = new StringBuilder();
  private String eventName = "";
  private String lastEventId;
  private boolean hasData;
  private boolean stopped;
  private boolean skipLf;
  private boolean atStart = true;
  private byte[] line = new byte[256];
  private int lineLength;

  private SseParser(Listener listener) {
    this.listener = listener;
  }

  /**
   * Reads {@code in} until end of stream (or until the listener stops it) and reports frames.
   *
   * @throws IOException if reading fails or a line exceeds {@link #MAX_LINE_BYTES}
   */
  @NonNull
  static Result parse(@NonNull InputStream in, @NonNull Listener listener) throws IOException {
    SseParser parser = new SseParser(listener);
    byte[] buffer = new byte[READ_BUFFER_BYTES];
    int read;
    while (!parser.stopped && (read = in.read(buffer)) != -1) {
      parser.feed(buffer, read);
    }
    boolean incomplete = !parser.stopped && (parser.hasData || parser.lineLength > 0);
    return new Result(parser.stopped, incomplete, parser.lastEventId);
  }

  private void feed(byte[] bytes, int count) throws IOException {
    for (int i = 0; i < count && !stopped; i++) {
      byte b = bytes[i];
      if (skipLf) {
        skipLf = false;
        if (b == '\n') {
          continue;
        }
      }
      if (b == '\n' || b == '\r') {
        skipLf = b == '\r';
        endLine();
      } else {
        append(b);
      }
    }
  }

  private void append(byte b) throws IOException {
    if (lineLength == line.length) {
      if (line.length >= MAX_LINE_BYTES) {
        throw new IOException("SSE line longer than " + MAX_LINE_BYTES + " bytes");
      }
      line = Arrays.copyOf(line, Math.min(line.length * 2, MAX_LINE_BYTES));
    }
    line[lineLength++] = b;
  }

  private void endLine() {
    int start = 0;
    if (atStart) {
      atStart = false;
      if (lineLength >= BOM.length
          && line[0] == BOM[0]
          && line[1] == BOM[1]
          && line[2] == BOM[2]) {
        start = BOM.length;
      }
    }
    String text = new String(line, start, lineLength - start, StandardCharsets.UTF_8);
    lineLength = 0;
    if (text.isEmpty()) {
      dispatch();
    } else if (text.charAt(0) == ':') {
      if (!listener.onComment(text.substring(1).trim())) {
        stopped = true;
      }
    } else {
      applyField(text);
    }
  }

  private void applyField(String text) {
    int colon = text.indexOf(':');
    String name = colon < 0 ? text : text.substring(0, colon);
    String value = colon < 0 ? "" : text.substring(colon + 1);
    if (value.startsWith(" ")) {
      value = value.substring(1);
    }
    switch (name) {
      case "data":
        data.append(value).append('\n');
        hasData = true;
        break;
      case "event":
        eventName = value;
        break;
      case "id":
        if (value.indexOf('\0') < 0) {
          lastEventId = value;
        }
        break;
      default:
        break;
    }
  }

  private void dispatch() {
    if (!hasData) {
      eventName = "";
      return;
    }
    int length = data.length();
    String payload = data.substring(0, length - 1);
    String name = eventName.isEmpty() ? "message" : eventName;
    data.setLength(0);
    hasData = false;
    eventName = "";
    if (!listener.onEvent(lastEventId, name, payload)) {
      stopped = true;
    }
  }
}

package ir.hanzodev1375.ghostide.postman.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.List;

/**
 * Turns a raw HTTP response into the text the response viewer shows: pretty-printed and
 * highlighted JSON, a header listing, and a display cap so a multi-megabyte body never reaches a
 * {@link android.widget.TextView} in one piece.
 *
 * <p>Everything here is pure string work and is meant to run off the main thread.
 */
public final class ResponseFormatter {

  /** Longest text handed to the viewer; the rest stays reachable through copy and share. */
  public static final int DISPLAY_CHAR_LIMIT = 120_000;

  /** Bodies longer than this are shown raw instead of being parsed and re-indented. */
  private static final int PRETTY_PRINT_CHAR_LIMIT = 1_000_000;

  private static final char LEFT_TO_RIGHT_MARK = '‎';

  private static final Gson PRETTY_GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

  private ResponseFormatter() {}

  /** The viewer-ready parts of one response. */
  public static final class Formatted {
    public final CharSequence body;
    public final CharSequence headers;
    public final boolean json;
    public final boolean clipped;

    Formatted(CharSequence body, CharSequence headers, boolean json, boolean clipped) {
      this.body = body;
      this.headers = headers;
      this.json = json;
      this.clipped = clipped;
    }
  }

  /**
   * Formats a response for display.
   *
   * @param body the decoded response body; {@code null} is treated as empty
   * @param headers response header name/value pairs; may be {@code null}
   * @param keyColor colour for JSON keys and header names
   * @param stringColor colour for JSON string values
   * @param numberColor colour for JSON numbers
   * @return display text; {@link Formatted#body} is empty for an empty body
   */
  @WorkerThread
  @NonNull
  public static Formatted format(
      @Nullable String body,
      @Nullable List<String[]> headers,
      int keyColor,
      int stringColor,
      int numberColor) {
    String raw = body == null ? "" : body;
    String pretty = prettyJsonOrNull(raw);
    boolean json = pretty != null;
    String text = json ? pretty : raw;

    boolean clipped = text.length() > DISPLAY_CHAR_LIMIT;
    if (clipped) {
      text = text.substring(0, clipEnd(text));
    }
    text = forceLeftToRightLines(text);

    CharSequence shownBody =
        json ? JsonUtils.highlight(text, keyColor, stringColor, numberColor) : text;
    return new Formatted(shownBody, formatHeaders(headers, keyColor), json, clipped);
  }

  @Nullable
  private static String prettyJsonOrNull(String raw) {
    if (raw.length() > PRETTY_PRINT_CHAR_LIMIT) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    char first = trimmed.charAt(0);
    if (first != '{' && first != '[') {
      return null;
    }
    try {
      JsonElement element = JsonParser.parseString(trimmed);
      if (!element.isJsonObject() && !element.isJsonArray()) {
        return null;
      }
      return PRETTY_GSON.toJson(element);
    } catch (JsonParseException | IllegalStateException notJson) {
      return null;
    }
  }

  private static int clipEnd(String text) {
    int limit = DISPLAY_CHAR_LIMIT;
    int newline = text.lastIndexOf('\n', limit);
    int end = newline > limit / 2 ? newline : limit;
    if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return end;
  }

  private static CharSequence formatHeaders(@Nullable List<String[]> headers, int nameColor) {
    SpannableStringBuilder out = new SpannableStringBuilder();
    if (headers == null) {
      return out;
    }
    for (String[] header : headers) {
      if (header == null || header.length < 2) {
        continue;
      }
      if (out.length() > 0) {
        out.append('\n');
      }
      String name = header[0] == null ? "" : header[0];
      String value = header[1] == null ? "" : header[1];
      if (containsRightToLeft(value)) {
        out.append(LEFT_TO_RIGHT_MARK);
      }
      int start = out.length();
      out.append(name);
      out.setSpan(
          new ForegroundColorSpan(nameColor), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      out.append(": ").append(value);
    }
    return out;
  }

  /**
   * The app runs with {@code supportsRtl="false"}, so {@code textDirection="ltr"} is ignored and
   * every line picks its direction from its first strong character. A JSON line that starts with
   * Persian text would then render right to left; a leading LRM keeps every line left to right.
   */
  private static String forceLeftToRightLines(String text) {
    if (!containsRightToLeft(text)) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length() + text.length() / 16 + 1);
    out.append(LEFT_TO_RIGHT_MARK);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      out.append(c);
      if (c == '\n') {
        out.append(LEFT_TO_RIGHT_MARK);
      }
    }
    return out.toString();
  }

  private static boolean containsRightToLeft(String text) {
    for (int i = 0; i < text.length(); i++) {
      byte direction = Character.getDirectionality(text.charAt(i));
      if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
          || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
        return true;
      }
    }
    return false;
  }
}

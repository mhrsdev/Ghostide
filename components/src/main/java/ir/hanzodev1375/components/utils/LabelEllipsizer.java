package ir.hanzodev1375.components.utils;

import android.icu.text.BreakIterator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Presentation-only shortening of short labels such as sheet titles, action labels and tab names.
 *
 * <p>Length is measured in user-perceived characters (extended grapheme clusters, as defined by
 * ICU), so a surrogate pair, a base letter with its combining marks, an emoji ZWJ sequence or a
 * flag is never split. The returned string is a new display string; callers keep the original for
 * content descriptions, tooltips, detail views and anything that is executed, such as commands.
 */
public final class LabelEllipsizer {

  /** The default number of visible characters, ellipsis included, for a short label. */
  public static final int DEFAULT_MAX_GRAPHEMES = 20;

  static final char ELLIPSIS = '…';
  static final char LRM = '‎';
  static final char RLM = '‏';
  static final char FSI = '⁨';
  static final char PDI = '⁩';

  private static final int MIN_GRAPHEMES = 2;

  private LabelEllipsizer() {}

  /**
   * Counts the user-perceived characters of {@code text}.
   *
   * @return the number of grapheme clusters, or 0 for {@code null} or empty text
   */
  public static int graphemeCount(@Nullable CharSequence text) {
    if (text == null || text.length() == 0) return 0;
    BreakIterator iterator = BreakIterator.getCharacterInstance();
    iterator.setText(text.toString());
    int count = 0;
    while (iterator.next() != BreakIterator.DONE) count++;
    return count;
  }

  /**
   * Shortens {@code text} to at most {@code maxGraphemes} visible characters. When it is longer,
   * the first {@code maxGraphemes - 1} grapheme clusters are kept (trailing white space removed)
   * and an ellipsis follows them. The ellipsis is followed by a directional mark matching the
   * label's first strong direction, so it stays on the trailing side of a Persian label (the
   * left) and of an English label (the right) even inside a paragraph of the other direction.
   *
   * @param text the original label; {@code null} is treated as empty
   * @param maxGraphemes the maximum number of visible characters, ellipsis included
   * @return {@code text} unchanged when it fits, otherwise the shortened display string
   * @throws IllegalArgumentException if {@code maxGraphemes} is smaller than 2
   */
  @NonNull
  public static String ellipsize(@Nullable CharSequence text, int maxGraphemes) {
    if (maxGraphemes < MIN_GRAPHEMES) {
      throw new IllegalArgumentException("maxGraphemes must be at least " + MIN_GRAPHEMES);
    }
    if (text == null) return "";
    String value = text.toString();
    if (value.isEmpty()) return value;
    BreakIterator iterator = BreakIterator.getCharacterInstance();
    iterator.setText(value);
    int keep = maxGraphemes - 1;
    int cut = 0;
    int count = 0;
    int boundary;
    while ((boundary = iterator.next()) != BreakIterator.DONE) {
      count++;
      if (count == keep) cut = boundary;
      if (count > maxGraphemes) break;
    }
    if (count <= maxGraphemes) return value;
    String head = stripTrailingWhitespace(value.substring(0, cut));
    char mark = isFirstStrongRtl(value) ? RLM : LRM;
    return head + ELLIPSIS + mark;
  }

  /**
   * Wraps {@code text} in a first-strong isolate, so a label keeps its own direction when it is
   * joined with other labels or embedded in text of the opposite direction.
   *
   * @param text the display string; {@code null} is treated as empty
   * @return the isolated string, or an empty string for empty input
   */
  @NonNull
  public static String isolate(@Nullable CharSequence text) {
    if (text == null || text.length() == 0) return "";
    return FSI + text.toString() + PDI;
  }

  /**
   * Returns whether the first strong directional character of {@code text} is right-to-left. Text
   * without a strong character is treated as left-to-right.
   */
  public static boolean isFirstStrongRtl(@Nullable CharSequence text) {
    if (text == null) return false;
    int length = text.length();
    int index = 0;
    while (index < length) {
      int codePoint = Character.codePointAt(text, index);
      byte direction = Character.getDirectionality(codePoint);
      if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
          || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
        return true;
      }
      if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
      index += Character.charCount(codePoint);
    }
    return false;
  }

  private static String stripTrailingWhitespace(String value) {
    int end = value.length();
    while (end > 0) {
      int codePoint = Character.codePointBefore(value, end);
      if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
      end -= Character.charCount(codePoint);
    }
    return value.substring(0, end);
  }
}

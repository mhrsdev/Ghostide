package ir.hanzodev1375.ghostide.vibe.ui.chat;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * A deliberately small Markdown reader for assistant answers. It understands only what coding
 * answers use: fenced code blocks, headings, bullet and numbered lists, paragraphs, and inside text
 * {@code `code`}, {@code **bold**}, {@code *italic*} and {@code [label](url)} (shown as its label;
 * no link is ever made clickable). Everything else is shown as plain text, so malformed or hostile
 * input can only produce text, never a view or an action.
 *
 * <p>Pure Java without Android classes; {@link #parse} never throws and an unterminated code
 * fence (an answer still streaming) is a code block that runs to the end.
 */
public final class MarkdownLite {

  /** A block level element. */
  public sealed interface Block {

    /** Plain paragraph; {@code text} still contains inline markers. */
    record Paragraph(String text) implements Block {}

    /** Heading of level 1 to 6. */
    record Heading(int level, String text) implements Block {}

    /**
     * One list entry; {@code marker} is the bullet or the number with its dot, {@code depth} the
     * nesting level (two spaces per level).
     */
    record ListItem(String marker, int depth, String text) implements Block {}

    /** Fenced code; {@code language} may be empty. */
    record Code(String language, String code) implements Block {}
  }

  /** Style bit: bold. */
  public static final int BOLD = 1;

  /** Style bit: italic. */
  public static final int ITALIC = 2;

  /** Style bit: inline code. */
  public static final int CODE = 4;

  /** A run of inline text with one style set. */
  public record Run(String text, int styles) {}

  private static final String FENCE = "```";
  private static final int MAX_HEADING = 6;
  private static final int MAX_DEPTH = 6;

  private MarkdownLite() {}

  /** Splits {@code source} into blocks. */
  @NonNull
  public static List<Block> parse(@NonNull String source) {
    List<Block> blocks = new ArrayList<>();
    String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    StringBuilder paragraph = new StringBuilder();
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      String trimmed = line.stripLeading();
      if (trimmed.startsWith(FENCE)) {
        flushParagraph(blocks, paragraph);
        String language = trimmed.substring(FENCE.length()).strip();
        StringBuilder code = new StringBuilder();
        i++;
        boolean first = true;
        while (i < lines.length && !lines[i].stripLeading().startsWith(FENCE)) {
          if (!first) {
            code.append('\n');
          }
          code.append(lines[i]);
          first = false;
          i++;
        }
        blocks.add(new Block.Code(language, code.toString()));
        i++;
        continue;
      }
      if (trimmed.isEmpty()) {
        flushParagraph(blocks, paragraph);
        i++;
        continue;
      }
      Block special = parseLineBlock(line, trimmed);
      if (special != null) {
        flushParagraph(blocks, paragraph);
        blocks.add(special);
      } else {
        if (paragraph.length() > 0) {
          paragraph.append('\n');
        }
        paragraph.append(trimmed);
      }
      i++;
    }
    flushParagraph(blocks, paragraph);
    return blocks;
  }

  private static Block parseLineBlock(String line, String trimmed) {
    int hashes = 0;
    while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') {
      hashes++;
    }
    if (hashes > 0
        && hashes <= MAX_HEADING
        && trimmed.length() > hashes
        && trimmed.charAt(hashes) == ' ') {
      return new Block.Heading(hashes, trimmed.substring(hashes + 1).strip());
    }
    int indent = line.length() - line.stripLeading().length();
    int depth = Math.min(indent / 2, MAX_DEPTH);
    if (trimmed.length() > 2
        && (trimmed.charAt(0) == '-' || trimmed.charAt(0) == '*' || trimmed.charAt(0) == '+')
        && trimmed.charAt(1) == ' ') {
      return new Block.ListItem("•", depth, trimmed.substring(2).strip());
    }
    int digits = 0;
    while (digits < trimmed.length() && Character.isDigit(trimmed.charAt(digits))) {
      digits++;
    }
    if (digits > 0
        && digits <= 3
        && trimmed.length() > digits + 1
        && trimmed.charAt(digits) == '.'
        && trimmed.charAt(digits + 1) == ' ') {
      return new Block.ListItem(
          trimmed.substring(0, digits + 1), depth, trimmed.substring(digits + 2).strip());
    }
    return null;
  }

  private static void flushParagraph(List<Block> blocks, StringBuilder paragraph) {
    if (paragraph.length() > 0) {
      blocks.add(new Block.Paragraph(paragraph.toString()));
      paragraph.setLength(0);
    }
  }

  /** Splits one line or paragraph of text into styled runs. */
  @NonNull
  public static List<Run> inline(@NonNull String text) {
    List<Run> runs = new ArrayList<>();
    parseInline(text, 0, runs);
    return runs;
  }

  private static void parseInline(String text, int inherited, List<Run> out) {
    StringBuilder plain = new StringBuilder();
    int i = 0;
    int length = text.length();
    while (i < length) {
      char c = text.charAt(i);
      if (c == '`') {
        int end = text.indexOf('`', i + 1);
        if (end > i + 1) {
          flush(out, plain, inherited);
          out.add(new Run(text.substring(i + 1, end), inherited | CODE));
          i = end + 1;
          continue;
        }
      } else if (c == '*' && i + 1 < length && text.charAt(i + 1) == '*') {
        int end = text.indexOf("**", i + 2);
        if (end > i + 2) {
          flush(out, plain, inherited);
          parseInline(text.substring(i + 2, end), inherited | BOLD, out);
          i = end + 2;
          continue;
        }
      } else if (c == '*' && i + 1 < length && !Character.isWhitespace(text.charAt(i + 1))) {
        int end = findItalicEnd(text, i + 1);
        if (end > i + 1) {
          flush(out, plain, inherited);
          parseInline(text.substring(i + 1, end), inherited | ITALIC, out);
          i = end + 1;
          continue;
        }
      } else if (c == '[') {
        int close = text.indexOf("](", i + 1);
        int end = close < 0 ? -1 : text.indexOf(')', close + 2);
        if (close > i + 1 && end > close) {
          flush(out, plain, inherited);
          parseInline(text.substring(i + 1, close), inherited, out);
          i = end + 1;
          continue;
        }
      }
      plain.append(c);
      i++;
    }
    flush(out, plain, inherited);
  }

  private static int findItalicEnd(String text, int from) {
    for (int j = from; j < text.length(); j++) {
      if (text.charAt(j) == '*'
          && !Character.isWhitespace(text.charAt(j - 1))
          && (j + 1 >= text.length() || text.charAt(j + 1) != '*')) {
        return j;
      }
    }
    return -1;
  }

  private static void flush(List<Run> out, StringBuilder plain, int styles) {
    if (plain.length() > 0) {
      out.add(new Run(plain.toString(), styles));
      plain.setLength(0);
    }
  }
}

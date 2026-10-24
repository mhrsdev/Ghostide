package ir.hanzodev1375.ghostide.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed view of a stack trace string in the format produced by {@code Log.getStackTraceString}
 * (exception line, optional message continuation lines, {@code at ...} frames, {@code Caused by:}
 * blocks and {@code ... N more} markers).
 *
 * <p>Parsing never throws: an empty or unrecognised input yields an instance whose {@link
 * #isEmpty()} is true or whose exception name is the raw first line.
 */
public final class StackTraceInfo {

  private static final String CAUSED_BY = "Caused by: ";
  private static final String SUPPRESSED = "Suppressed: ";
  private static final Pattern FRAME =
      Pattern.compile("^\\s*at\\s+([\\w$.<>/]+)\\.([\\w$<>\\-]+)\\((.*)\\)\\s*$");

  /** Kind of one line of the trace, used to style it. */
  public enum LineKind {
    HEADER,
    MESSAGE,
    CAUSE,
    FRAME,
    OWN_FRAME,
    MORE
  }

  private final List<String> lines;
  private final List<LineKind> kinds;
  private final String exceptionName;
  private final String message;
  @Nullable private final String rootCauseName;
  @Nullable private final String originFile;
  @Nullable private final String originMethod;

  private StackTraceInfo(
      List<String> lines,
      List<LineKind> kinds,
      String exceptionName,
      String message,
      @Nullable String rootCauseName,
      @Nullable String originFile,
      @Nullable String originMethod) {
    this.lines = lines;
    this.kinds = kinds;
    this.exceptionName = exceptionName;
    this.message = message;
    this.rootCauseName = rootCauseName;
    this.originFile = originFile;
    this.originMethod = originMethod;
  }

  /**
   * Parses {@code trace}.
   *
   * @param trace the stack trace text, may be null or empty
   * @param ownPackagePrefix class-name prefix of this app's own code (for example {@code
   *     "ir.hanzodev1375."}); the first frame starting with it becomes the origin
   * @return the parsed trace, never null
   */
  @NonNull
  public static StackTraceInfo parse(@Nullable String trace, @NonNull String ownPackagePrefix) {
    if (trace == null || trace.trim().isEmpty()) {
      return new StackTraceInfo(
          Collections.emptyList(), Collections.emptyList(), "", "", null, null, null);
    }
    String[] raw = trace.replace("\r", "").split("\n");
    List<String> lines = new ArrayList<>(raw.length);
    List<LineKind> kinds = new ArrayList<>(raw.length);
    String header = null;
    StringBuilder message = new StringBuilder();
    String rootCause = null;
    String originFile = null;
    String originMethod = null;
    boolean framesStarted = false;

    for (String line : raw) {
      if (line.trim().isEmpty()) {
        continue;
      }
      String text = line.replace("\t", "  ");
      String trimmed = line.trim();
      LineKind kind;
      if (header == null) {
        header = trimmed;
        kind = LineKind.HEADER;
      } else if (trimmed.startsWith(CAUSED_BY) || trimmed.startsWith(SUPPRESSED)) {
        framesStarted = true;
        kind = LineKind.CAUSE;
        if (trimmed.startsWith(CAUSED_BY)) {
          rootCause = trimmed.substring(CAUSED_BY.length());
        }
      } else if (trimmed.startsWith("at ")) {
        framesStarted = true;
        Matcher matcher = FRAME.matcher(trimmed);
        boolean own = false;
        if (matcher.matches()) {
          String cls = matcher.group(1);
          own = cls.startsWith(ownPackagePrefix);
          if (own && originFile == null) {
            originFile = matcher.group(3);
            originMethod = simpleName(cls) + "." + matcher.group(2);
          }
        }
        kind = own ? LineKind.OWN_FRAME : LineKind.FRAME;
      } else if (trimmed.startsWith("...")) {
        kind = LineKind.MORE;
      } else if (!framesStarted) {
        kind = LineKind.MESSAGE;
        message.append('\n').append(trimmed);
      } else {
        kind = LineKind.MESSAGE;
      }
      lines.add(text);
      kinds.add(kind);
    }

    String name = header == null ? "" : header;
    String firstMessage = "";
    int colon = name.indexOf(": ");
    if (colon > 0) {
      firstMessage = name.substring(colon + 2);
      name = name.substring(0, colon);
    }
    String fullMessage = (firstMessage + message).trim();
    return new StackTraceInfo(
        lines,
        kinds,
        name,
        fullMessage,
        rootCause == null ? null : exceptionPart(rootCause),
        originFile,
        originMethod);
  }

  private static String exceptionPart(String line) {
    int colon = line.indexOf(": ");
    return colon > 0 ? line.substring(0, colon) : line;
  }

  /** @return the part after the last dot, or {@code qualified} itself when it has none. */
  @NonNull
  public static String simpleName(@NonNull String qualified) {
    int dot = qualified.lastIndexOf('.');
    return dot >= 0 && dot < qualified.length() - 1 ? qualified.substring(dot + 1) : qualified;
  }

  /** @return true when the input had no non-blank line. */
  public boolean isEmpty() {
    return lines.isEmpty();
  }

  /** @return the fully qualified name of the top-level exception, e.g. {@code java.lang.Error}. */
  @NonNull
  public String getExceptionName() {
    return exceptionName;
  }

  /** @return the exception message (may span several lines), or an empty string. */
  @NonNull
  public String getMessage() {
    return message;
  }

  /** @return the qualified name of the innermost {@code Caused by:} exception, or null. */
  @Nullable
  public String getRootCauseName() {
    return rootCauseName;
  }

  /** @return the source position of the first own frame, e.g. {@code Foo.java:42}, or null. */
  @Nullable
  public String getOriginFile() {
    return originFile;
  }

  /** @return {@code SimpleClass.method} of the first own frame, or null. */
  @Nullable
  public String getOriginMethod() {
    return originMethod;
  }

  /** @return the non-blank lines of the trace, tabs expanded to two spaces. */
  @NonNull
  public List<String> getLines() {
    return Collections.unmodifiableList(lines);
  }

  /** @return the kind of each entry of {@link #getLines()}, index for index. */
  @NonNull
  public List<LineKind> getKinds() {
    return Collections.unmodifiableList(kinds);
  }
}

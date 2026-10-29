package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the sign-in URL and the user code from the output of {@code hermes auth add
 * openai-codex --no-browser}. ANSI colour codes are removed first. Only an {@code https} URL on
 * {@code auth.openai.com} is accepted, so text printed by the process can never make the app open
 * an arbitrary address.
 */
final class DeviceCodeParser {

  private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
  private static final Pattern URL =
      Pattern.compile("https://auth\\.openai\\.com/[A-Za-z0-9/_\\-.]*");
  private static final Pattern CODE = Pattern.compile("[A-Z0-9]{3,10}-[A-Z0-9]{3,10}");
  private static final Pattern FAILURE = Pattern.compile("^Login failed:\\s*(.*)$");

  private String url;
  private String code;
  private String failure;

  /** Consumes one output line. */
  void onLine(@NonNull String rawLine) {
    String line = ANSI.matcher(rawLine).replaceAll("").trim();
    if (line.isEmpty()) {
      return;
    }
    Matcher failureMatcher = FAILURE.matcher(line);
    if (failureMatcher.matches()) {
      failure = failureMatcher.group(1);
      return;
    }
    if (url == null) {
      Matcher urlMatcher = URL.matcher(line);
      if (urlMatcher.find()) {
        url = urlMatcher.group();
      }
      return;
    }
    if (code == null && CODE.matcher(line).matches()) {
      code = line;
    }
  }

  /** True once both the URL and the code were seen. */
  boolean isReady() {
    return url != null && code != null;
  }

  @Nullable
  String url() {
    return url;
  }

  @Nullable
  String code() {
    return code;
  }

  /** The reason Hermes printed after {@code Login failed:}, or {@code null}. */
  @Nullable
  String failure() {
    return failure;
  }
}

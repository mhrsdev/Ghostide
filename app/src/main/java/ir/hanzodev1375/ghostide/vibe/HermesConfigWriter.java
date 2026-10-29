package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Produces Hermes' {@code config.yaml} for the selected provider and workspace. The file contains
 * no secrets: API keys are passed to the process through its environment (see {@link
 * #secretEnvName}). Tool approvals are forced to {@code manual} with a 10 minute window after which
 * Hermes denies the command.
 */
final class HermesConfigWriter {

  static final String NAMED_PROVIDER = "ghost-vibe";
  static final String ENV_OPENAI_COMPATIBLE_KEY = "GHOST_VIBE_API_KEY";
  static final String ENV_ANTHROPIC_KEY = "ANTHROPIC_API_KEY";
  static final int APPROVAL_TIMEOUT_SECONDS = 600;

  private HermesConfigWriter() {}

  /** The environment variable that carries the provider's API key, or {@code null} if none. */
  @Nullable
  static String secretEnvName(@NonNull VibeProviderType type) {
    switch (type) {
      case ANTHROPIC:
        return ENV_ANTHROPIC_KEY;
      case OPENAI_COMPATIBLE:
        return ENV_OPENAI_COMPATIBLE_KEY;
      default:
        return null;
    }
  }

  /**
   * Builds the YAML text.
   *
   * @param guestWorkspace the project folder as seen inside proot
   */
  @NonNull
  static String buildYaml(@NonNull VibeProviderConfig config, @NonNull String guestWorkspace)
      throws IOException {
    StringBuilder yaml = new StringBuilder(512);
    yaml.append("model:\n");
    switch (config.getType()) {
      case ANTHROPIC:
        yaml.append("  provider: anthropic\n");
        if (!config.getBaseUrl().isEmpty()) {
          yaml.append("  base_url: ").append(quote(config.getBaseUrl())).append('\n');
        }
        break;
      case OPENAI_COMPATIBLE:
        yaml.append("  provider: ").append(NAMED_PROVIDER).append('\n');
        break;
      default:
        yaml.append("  provider: openai-codex\n");
        break;
    }
    yaml.append("  default: ").append(quote(config.getModel())).append('\n');
    if (config.getType() == VibeProviderType.OPENAI_COMPATIBLE) {
      yaml.append("providers:\n")
          .append("  ")
          .append(NAMED_PROVIDER)
          .append(":\n")
          .append("    api: ")
          .append(quote(config.getBaseUrl()))
          .append('\n')
          .append("    key_env: ")
          .append(ENV_OPENAI_COMPATIBLE_KEY)
          .append('\n')
          .append("    default_model: ")
          .append(quote(config.getModel()))
          .append('\n');
    }
    yaml.append("terminal:\n")
        .append("  backend: local\n")
        .append("  cwd: ")
        .append(quote(guestWorkspace))
        .append('\n')
        .append("approvals:\n")
        .append("  mode: manual\n")
        .append("  timeout: ")
        .append(APPROVAL_TIMEOUT_SECONDS)
        .append('\n');
    return yaml.toString();
  }

  /** Writes {@code yaml} atomically with owner-only permissions, creating the folder if needed. */
  static void write(@NonNull Path configFile, @NonNull String yaml) throws IOException {
    VibeFiles.createPrivateDirectory(configFile.toAbsolutePath().getParent());
    VibeFiles.writeAtomically(configFile, yaml.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Quotes {@code value} as a YAML single-quoted scalar. Control characters are rejected because
   * they cannot be represented safely in that style.
   */
  @NonNull
  static String quote(@NonNull String value) throws IOException {
    for (int i = 0; i < value.length(); i++) {
      if (value.charAt(i) < ' ' || value.charAt(i) == 0x7f) {
        throw new IOException("value contains control characters");
      }
    }
    return "'" + value.replace("'", "''") + "'";
  }
}

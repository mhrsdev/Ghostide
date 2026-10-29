package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import java.util.Objects;
import java.util.regex.Pattern;
import okhttp3.HttpUrl;

/**
 * Immutable, validated, secret-free description of the model provider the agent talks to.
 *
 * <p>The API key (if the type needs one) is never part of this object; it lives in {@link
 * VibeProviderStore} and reaches the Hermes process only through its standard input.
 */
public final class VibeProviderConfig {

  private static final int MAX_MODEL_LENGTH = 200;
  private static final int MAX_URL_LENGTH = 300;
  private static final Pattern MODEL_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@+\\-]*");

  private final VibeProviderType type;
  private final String baseUrl;
  private final String model;

  private VibeProviderConfig(VibeProviderType type, String baseUrl, String model) {
    this.type = type;
    this.baseUrl = baseUrl;
    this.model = model;
  }

  /**
   * Validates and creates a configuration.
   *
   * @param type the provider kind
   * @param baseUrl base URL such as {@code https://api.openai.com/v1}; required for {@link
   *     VibeProviderType#OPENAI_COMPATIBLE}, optional for {@link VibeProviderType#ANTHROPIC}
   *     (empty means the official endpoint) and ignored for {@link
   *     VibeProviderType#CHATGPT_ACCOUNT}; may be {@code null}
   * @param model the model id sent to the provider
   * @throws VibeConfigException if a field cannot be used; {@link VibeConfigException#getProblem()}
   *     says which one
   */
  @NonNull
  public static VibeProviderConfig create(
      @NonNull VibeProviderType type, String baseUrl, String model) throws VibeConfigException {
    Objects.requireNonNull(type, "type");
    String cleanModel = model == null ? "" : model.trim();
    if (cleanModel.isEmpty()) {
      throw new VibeConfigException(VibeConfigException.Problem.MODEL_EMPTY, "model is empty");
    }
    if (cleanModel.length() > MAX_MODEL_LENGTH || !MODEL_PATTERN.matcher(cleanModel).matches()) {
      throw new VibeConfigException(VibeConfigException.Problem.MODEL_INVALID, "model id is invalid");
    }
    String cleanUrl = baseUrl == null ? "" : baseUrl.trim();
    switch (type) {
      case OPENAI_COMPATIBLE:
        if (cleanUrl.isEmpty()) {
          throw new VibeConfigException(
              VibeConfigException.Problem.BASE_URL_REQUIRED, "base URL is required");
        }
        cleanUrl = normalizeUrl(cleanUrl);
        break;
      case ANTHROPIC:
        if (!cleanUrl.isEmpty()) {
          cleanUrl = normalizeUrl(cleanUrl);
          if (isCleartext(cleanUrl) && !isLoopbackHost(cleanUrl)) {
            throw new VibeConfigException(
                VibeConfigException.Problem.BASE_URL_INSECURE,
                "an Anthropic key must not be sent over cleartext http");
          }
        }
        break;
      default:
        cleanUrl = "";
        break;
    }
    return new VibeProviderConfig(type, cleanUrl, cleanModel);
  }

  private static String normalizeUrl(String raw) throws VibeConfigException {
    HttpUrl parsed = raw.length() > MAX_URL_LENGTH ? null : HttpUrl.parse(raw);
    boolean usable =
        parsed != null
            && parsed.username().isEmpty()
            && parsed.password().isEmpty()
            && parsed.query() == null
            && parsed.fragment() == null;
    if (!usable) {
      throw new VibeConfigException(
          VibeConfigException.Problem.BASE_URL_INVALID, "base URL is not a plain http(s) URL");
    }
    String text = parsed.toString();
    return text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
  }

  private static boolean isCleartext(String url) {
    return url.regionMatches(true, 0, "http://", 0, 7);
  }

  private static boolean isLoopbackHost(String url) {
    HttpUrl parsed = HttpUrl.parse(url);
    if (parsed == null) {
      return false;
    }
    String host = parsed.host();
    return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
  }

  /** The provider kind. */
  @NonNull
  public VibeProviderType getType() {
    return type;
  }

  /**
   * The normalized base URL without trailing slash; empty when the provider's official endpoint is
   * used.
   */
  @NonNull
  public String getBaseUrl() {
    return baseUrl;
  }

  /** The model id, trimmed. */
  @NonNull
  public String getModel() {
    return model;
  }

  /** True when the base URL uses cleartext http; the UI should warn before saving. */
  public boolean isCleartext() {
    return isCleartext(baseUrl);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof VibeProviderConfig)) {
      return false;
    }
    VibeProviderConfig that = (VibeProviderConfig) other;
    return type == that.type && baseUrl.equals(that.baseUrl) && model.equals(that.model);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, baseUrl, model);
  }

  @Override
  public String toString() {
    return "VibeProviderConfig{type=" + type + ", baseUrl=" + baseUrl + ", model=" + model + "}";
  }
}

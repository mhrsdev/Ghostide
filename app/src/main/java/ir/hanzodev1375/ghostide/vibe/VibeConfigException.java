package ir.hanzodev1375.ghostide.vibe;

/** Thrown when a {@link VibeProviderConfig} would be unusable; names the offending field. */
public final class VibeConfigException extends Exception {

  private static final long serialVersionUID = 1L;

  /** What is wrong with the supplied configuration. */
  public enum Problem {
    /** The model id is empty. */
    MODEL_EMPTY,
    /** The model id has unsupported characters or is too long. */
    MODEL_INVALID,
    /** An OpenAI-compatible provider needs a base URL. */
    BASE_URL_REQUIRED,
    /** The base URL is not a plain http or https URL, or embeds credentials, a query or a fragment. */
    BASE_URL_INVALID,
    /** The base URL of an Anthropic provider uses cleartext http towards a non-loopback host. */
    BASE_URL_INSECURE,
    /** The provider needs an API key and none is stored or supplied. */
    SECRET_MISSING,
    /** The API key contains whitespace or control characters or is unreasonably long. */
    SECRET_INVALID
  }

  private final Problem problem;

  VibeConfigException(Problem problem, String message) {
    super(message);
    this.problem = problem;
  }

  /** The kind of problem, suitable for choosing a localized message. */
  public Problem getProblem() {
    return problem;
  }
}

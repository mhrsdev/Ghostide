package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.IOException;

/**
 * Failure of a call to the local Hermes API server. {@link #getKind()} is the stable thing to
 * branch on; the message is a diagnostic for logs and never contains keys.
 */
public final class HermesApiException extends IOException {

  private static final long serialVersionUID = 1L;

  /** Category of the failure. */
  public enum Kind {
    /** The server could not be reached or the connection broke. Usually retryable. */
    NETWORK(true),
    /** A connect, read or call timeout elapsed. Usually retryable. */
    TIMEOUT(true),
    /** HTTP 401: the bearer key was rejected (server restarted with another key). */
    UNAUTHORIZED(false),
    /** HTTP 404: unknown run, or an endpoint this Hermes build does not have. */
    NOT_FOUND(false),
    /** HTTP 409: the request conflicts with the run state (no pending approval, stale prompt...). */
    CONFLICT(false),
    /** HTTP 400/422: the server rejected the request body. */
    BAD_REQUEST(false),
    /** HTTP 429: too many concurrent runs. Retry later. */
    RATE_LIMITED(true),
    /** HTTP 503: the server is draining or its storage is busy. Retry later. */
    UNAVAILABLE(true),
    /** Any other HTTP 5xx. */
    SERVER_ERROR(false),
    /** The response did not have the expected shape. */
    PROTOCOL(false),
    /** The Hermes build does not support the requested operation (for example steering). */
    UNSUPPORTED(false),
    /** The call was cancelled locally. */
    CANCELLED(false);

    private final boolean retryable;

    Kind(boolean retryable) {
      this.retryable = retryable;
    }

    /** True if repeating the same call later may succeed. */
    public boolean isRetryable() {
      return retryable;
    }
  }

  private final Kind kind;
  private final int httpStatus;
  private final String errorCode;

  HermesApiException(
      @NonNull Kind kind,
      @NonNull String message,
      int httpStatus,
      @Nullable String errorCode,
      @Nullable Throwable cause) {
    super(message, cause);
    this.kind = kind;
    this.httpStatus = httpStatus;
    this.errorCode = errorCode == null ? "" : errorCode;
  }

  HermesApiException(@NonNull Kind kind, @NonNull String message, @Nullable Throwable cause) {
    this(kind, message, 0, null, cause);
  }

  /** The failure category. */
  @NonNull
  public Kind getKind() {
    return kind;
  }

  /** The HTTP status, or 0 if no response was received. */
  public int getHttpStatus() {
    return httpStatus;
  }

  /** Hermes' {@code error.code} (for example {@code approval_not_pending}), or empty. */
  @NonNull
  public String getErrorCode() {
    return errorCode;
  }

  static Kind kindForStatus(int status) {
    switch (status) {
      case 400:
      case 422:
        return Kind.BAD_REQUEST;
      case 401:
      case 403:
        return Kind.UNAUTHORIZED;
      case 404:
        return Kind.NOT_FOUND;
      case 409:
        return Kind.CONFLICT;
      case 429:
        return Kind.RATE_LIMITED;
      case 503:
        return Kind.UNAVAILABLE;
      default:
        return status >= 500 ? Kind.SERVER_ERROR : Kind.PROTOCOL;
    }
  }
}

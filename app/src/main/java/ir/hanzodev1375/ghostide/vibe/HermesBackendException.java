package ir.hanzodev1375.ghostide.vibe;

import java.util.Collections;
import java.util.List;

/** A failed install or launch, with the {@link HermesRuntime.FailureReason} to surface. */
final class HermesBackendException extends Exception {

  private static final long serialVersionUID = 1L;

  private final HermesRuntime.FailureReason reason;
  private final transient List<String> logTail;

  HermesBackendException(HermesRuntime.FailureReason reason, String message, Throwable cause) {
    this(reason, message, cause, Collections.emptyList());
  }

  HermesBackendException(
      HermesRuntime.FailureReason reason, String message, Throwable cause, List<String> logTail) {
    super(message, cause);
    this.reason = reason;
    this.logTail = Collections.unmodifiableList(logTail);
  }

  HermesRuntime.FailureReason getReason() {
    return reason;
  }

  List<String> getLogTail() {
    return logTail;
  }
}

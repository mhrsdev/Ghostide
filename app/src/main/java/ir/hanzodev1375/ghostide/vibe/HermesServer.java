package ir.hanzodev1375.ghostide.vibe;

import java.util.List;

/** A launched Hermes API server process. */
interface HermesServer {

  /** Loopback root URL such as {@code http://127.0.0.1:41233}. */
  String baseUrl();

  /** The bearer key the server was started with. */
  String apiKey();

  /** True while the process runs. */
  boolean isAlive();

  /** The exit code, or {@code Integer.MIN_VALUE} while the process runs. */
  int exitCode();

  /** True when {@code GET /health} answers ok; never throws. */
  boolean isHealthy();

  /** The last lines of the server's output with secrets masked. */
  List<String> recentLog();

  /** Registers the single callback run (on a background thread) when the process ends. */
  void setExitListener(Runnable listener);

  /** Terminates the process gracefully, then forcibly; blocks up to a few seconds; idempotent. */
  void stop();
}

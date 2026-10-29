package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Signs a ChatGPT account in to the local Hermes through OpenAI's device-code flow, driven by
 * Hermes' own {@code hermes auth add openai-codex --no-browser} command.
 *
 * <p>Sequence: {@link Listener#onCodeReady} gives the page to open in the browser and the code to
 * type there, then the helper waits (up to about 15 minutes, Hermes' own limit) for the user to
 * finish; {@link Listener#onSucceeded} or {@link Listener#onFailed} ends it. The tokens are stored
 * by Hermes in its private home, never seen by this app. All callbacks run on the main thread and
 * exactly one of succeeded/failed is delivered unless {@link #cancel()} is called first.
 *
 * <p>The command's text output is not a stable API. If its layout changes the helper fails with
 * {@link Failure#CODE_NOT_FOUND}; the user can then run {@link #TERMINAL_COMMAND} in a Debian
 * terminal session instead.
 */
public final class ChatGptAccountLogin {

  /**
   * The command to run in a Debian terminal session when the in-app flow cannot be used. It
   * prints the same URL and code and stores the login for the in-app agent.
   */
  public static final String TERMINAL_COMMAND =
      "HERMES_HOME="
          + HermesPaths.GUEST_HERMES_HOME
          + " "
          + HermesPaths.GUEST_HERMES_BIN
          + " auth add openai-codex --type oauth --no-browser";

  /** Why a login did not complete. */
  public enum Failure {
    /** Hermes is not installed yet. */
    NOT_INSTALLED,
    /** The login process could not be started. */
    START_FAILED,
    /** The process ended without printing a recognizable URL and code. */
    CODE_NOT_FOUND,
    /** OpenAI or Hermes rejected the login (denied, expired, throttled). */
    REJECTED,
    /** The user did not finish in time. */
    TIMEOUT
  }

  /** Callbacks, all on the main thread. */
  public interface Listener {
    /**
     * Show {@code userCode} and open {@code verificationUrl} (always an https auth.openai.com page)
     * in the browser.
     */
    void onCodeReady(@NonNull String verificationUrl, @NonNull String userCode);

    /** The account is signed in. */
    void onSucceeded();

    /** The login ended without success; {@code detail} is a short diagnostic (may be empty). */
    void onFailed(@NonNull Failure failure, @NonNull String detail);
  }

  /** Starts the login process; abstracted for tests. */
  interface ProcessFactory {
    ProotLauncher.Started start() throws IOException;
  }

  private static final long LOGIN_TIMEOUT_MILLIS = 16L * 60 * 1000;

  private final ProcessFactory factory;
  private final Executor io;
  private final MainThreadPoster main;
  private final Listener listener;
  private final OperationToken token = new OperationToken();
  private final AtomicBoolean ended = new AtomicBoolean();
  private final long timeoutMillis;

  ChatGptAccountLogin(
      ProcessFactory factory, Executor io, MainThreadPoster main, Listener listener) {
    this(factory, io, main, listener, LOGIN_TIMEOUT_MILLIS);
  }

  ChatGptAccountLogin(
      ProcessFactory factory,
      Executor io,
      MainThreadPoster main,
      Listener listener,
      long timeoutMillis) {
    this.factory = factory;
    this.io = io;
    this.main = main;
    this.listener = listener;
    this.timeoutMillis = timeoutMillis;
  }

  /** The guest environment and command of the login process. */
  static Map<String, String> guestEnvironment() {
    Map<String, String> env = ProotCommand.baseGuestEnv();
    env.put("HERMES_HOME", HermesPaths.GUEST_HERMES_HOME);
    env.put("PYTHONUNBUFFERED", "1");
    return env;
  }

  static List<String> guestCommand() {
    return Arrays.asList(
        HermesPaths.GUEST_HERMES_BIN, "auth", "add", "openai-codex", "--type", "oauth", "--no-browser");
  }

  void start() {
    try {
      io.execute(this::run);
    } catch (RejectedExecutionException e) {
      end(Failure.START_FAILED, "no background thread available");
    }
  }

  /** Abandons the login; no further callback is delivered. */
  public void cancel() {
    if (ended.compareAndSet(false, true)) {
      token.cancel();
    }
  }

  private void run() {
    ProotLauncher.Started started;
    try {
      started = factory.start();
    } catch (IOException e) {
      end(Failure.START_FAILED, String.valueOf(e.getMessage()));
      return;
    }
    Process process = started.process;
    token.setOnCancel(process::destroy);
    Thread watchdog = startWatchdog();
    DeviceCodeParser parser = new DeviceCodeParser();
    boolean announced = false;
    int exitCode = -1;
    try {
      process.getOutputStream().close();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          parser.onLine(line);
          if (!announced && parser.isReady()) {
            announced = true;
            String url = parser.url();
            String code = parser.code();
            main.post(
                () -> {
                  if (!ended.get()) {
                    listener.onCodeReady(url, code);
                  }
                },
                0);
          }
        }
      }
      exitCode = process.waitFor();
    } catch (IOException e) {
      process.destroyForcibly();
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    } finally {
      token.clearOnCancel();
      watchdog.interrupt();
      started.cleanup.run();
    }
    if (ended.get()) {
      return;
    }
    if (exitCode == 0 && announced) {
      end(null, "");
    } else if (token.isCancelled()) {
      end(Failure.TIMEOUT, "");
    } else if (!announced) {
      end(Failure.CODE_NOT_FOUND, valueOrEmpty(parser.failure()));
    } else {
      end(Failure.REJECTED, valueOrEmpty(parser.failure()));
    }
  }

  private Thread startWatchdog() {
    Thread watchdog =
        new Thread(
            () -> {
              try {
                Thread.sleep(timeoutMillis);
                token.cancel();
              } catch (InterruptedException finished) {
                Thread.currentThread().interrupt();
              }
            },
            "hermes-login-watchdog");
    watchdog.setDaemon(true);
    watchdog.start();
    return watchdog;
  }

  private void end(@Nullable Failure failure, String detail) {
    if (!ended.compareAndSet(false, true)) {
      return;
    }
    main.post(
        () -> {
          if (failure == null) {
            listener.onSucceeded();
          } else {
            listener.onFailed(failure, detail);
          }
        },
        0);
  }

  private static String valueOrEmpty(@Nullable String value) {
    return value == null ? "" : value;
  }
}

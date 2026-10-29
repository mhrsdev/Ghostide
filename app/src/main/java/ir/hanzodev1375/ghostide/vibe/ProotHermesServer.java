package ir.hanzodev1375.ghostide.vibe;

import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A Hermes API server running as a child process (normally proot). A daemon thread drains the
 * merged stdout/stderr into a small ring buffer with secrets masked, so the process can never block
 * on a full pipe and failures can be shown to the user without leaking keys.
 */
final class ProotHermesServer implements HermesServer {

  private static final String TAG = "GhostVibeServer";
  private static final int LOG_LINES = 60;
  private static final int MIN_SECRET_LENGTH_TO_MASK = 6;
  private static final long TERM_WAIT_SECONDS = 8;
  private static final long KILL_WAIT_SECONDS = 2;

  private final Process process;
  private final String baseUrl;
  private final String apiKey;
  private final HermesApiClient healthClient;
  private final List<String> secrets;
  private final Runnable cleanup;
  private final ArrayDeque<String> log = new ArrayDeque<>();
  private final Object exitLock = new Object();
  private Runnable exitListener;
  private boolean exited;
  private boolean exitDelivered;

  ProotHermesServer(
      Process process,
      String baseUrl,
      String apiKey,
      Collection<String> secretsToMask,
      Runnable cleanup) {
    this.process = process;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.healthClient = new HermesApiClient(baseUrl, apiKey);
    this.secrets = new ArrayList<>(secretsToMask);
    this.cleanup = cleanup;
    Thread reader = new Thread(this::drainOutput, "hermes-server-log");
    reader.setDaemon(true);
    reader.start();
  }

  @Override
  public String baseUrl() {
    return baseUrl;
  }

  @Override
  public String apiKey() {
    return apiKey;
  }

  @Override
  public boolean isAlive() {
    return process.isAlive();
  }

  @Override
  public int exitCode() {
    return process.isAlive() ? Integer.MIN_VALUE : process.exitValue();
  }

  @Override
  public boolean isHealthy() {
    try {
      healthClient.health(null);
      return true;
    } catch (HermesApiException e) {
      return false;
    }
  }

  @Override
  public List<String> recentLog() {
    synchronized (log) {
      return new ArrayList<>(log);
    }
  }

  @Override
  public void setExitListener(Runnable listener) {
    boolean deliverNow;
    synchronized (exitLock) {
      exitListener = listener;
      deliverNow = exited && !exitDelivered;
      if (deliverNow) {
        exitDelivered = true;
      }
    }
    if (deliverNow) {
      listener.run();
    }
  }

  @Override
  public void stop() {
    process.destroy();
    try {
      if (!process.waitFor(TERM_WAIT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(KILL_WAIT_SECONDS, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }

  private void drainOutput() {
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        remember(mask(line));
      }
    } catch (IOException e) {
      Log.w(TAG, "server output closed: " + e.getClass().getSimpleName());
    }
    try {
      process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    cleanup.run();
    Runnable toRun;
    synchronized (exitLock) {
      exited = true;
      toRun = exitDelivered ? null : exitListener;
      if (toRun != null) {
        exitDelivered = true;
      }
    }
    if (toRun != null) {
      toRun.run();
    }
  }

  private String mask(String line) {
    String masked = line;
    for (String secret : secrets) {
      if (secret.length() >= MIN_SECRET_LENGTH_TO_MASK && masked.contains(secret)) {
        masked = masked.replace(secret, "***");
      }
    }
    return masked;
  }

  private void remember(String line) {
    synchronized (log) {
      if (log.size() == LOG_LINES) {
        log.removeFirst();
      }
      log.addLast(line);
    }
  }
}

package ir.hanzodev1375.ghostide.vibe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Cancellation handle of one long operation (install, start, login). Cancelling is idempotent,
 * wakes sleepers and runs the registered action (typically "destroy the child process") exactly
 * once, immediately if the token was already cancelled when the action is registered.
 */
final class OperationToken {

  private final CountDownLatch cancelledLatch = new CountDownLatch(1);
  private final Object lock = new Object();
  private boolean cancelled;
  private Runnable onCancel;

  /** Cancels the operation. */
  void cancel() {
    Runnable action;
    synchronized (lock) {
      if (cancelled) {
        return;
      }
      cancelled = true;
      action = onCancel;
      onCancel = null;
    }
    cancelledLatch.countDown();
    if (action != null) {
      action.run();
    }
  }

  boolean isCancelled() {
    return cancelledLatch.getCount() == 0;
  }

  /** Registers the action to run on cancellation; replaces an earlier one. */
  void setOnCancel(Runnable action) {
    boolean runNow;
    synchronized (lock) {
      runNow = cancelled;
      onCancel = runNow ? null : action;
    }
    if (runNow && action != null) {
      action.run();
    }
  }

  /** Removes the registered action (the guarded resource is gone). */
  void clearOnCancel() {
    synchronized (lock) {
      onCancel = null;
    }
  }

  /**
   * Sleeps up to {@code millis}.
   *
   * @return true if the token was cancelled (or the thread interrupted) during the wait
   */
  boolean sleepUnlessCancelled(long millis) {
    try {
      return cancelledLatch.await(millis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return true;
    }
  }
}

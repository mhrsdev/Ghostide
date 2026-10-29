package ir.hanzodev1375.ghostide.vibe;

import okhttp3.Call;

/**
 * Cancels a blocking {@link HermesApiClient} call from another thread. One instance can guard
 * several consecutive calls; once cancelled it stays cancelled and later calls fail immediately.
 */
public final class HermesCancellation {

  private volatile boolean cancelled;
  private volatile Call activeCall;

  /** Cancels the call in flight, if any, and every call started afterwards. */
  public void cancel() {
    cancelled = true;
    Call call = activeCall;
    if (call != null) {
      call.cancel();
    }
  }

  /** True once {@link #cancel()} was invoked. */
  public boolean isCancelled() {
    return cancelled;
  }

  void attach(Call call) {
    activeCall = call;
    if (cancelled) {
      call.cancel();
    }
  }

  void detach(Call call) {
    if (activeCall == call) {
      activeCall = null;
    }
  }
}

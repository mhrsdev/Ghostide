package ir.hanzodev1375.ghostide.vibe;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Coalesces the events of a run into batches delivered on the main thread at a bounded rate.
 *
 * <p>Adjacent {@link VibeEvent.MessageDelta}s are merged into one, so a fast token stream costs the
 * UI at most {@code 1000 / minIntervalMillis} updates per second and the pending memory is
 * proportional to the text, not to the number of chunks. Other events are never merged or
 * dropped, keep their order and are delivered without extra delay. Producers may call from any
 * thread; the sink is always invoked through the {@link MainThreadPoster}.
 */
final class EventBatcher {

  /** Receives batches on the main thread. */
  interface Sink {
    void deliver(@NonNull List<VibeEvent> batch);
  }

  private final MainThreadPoster poster;
  private final Sink sink;
  private final long minIntervalMillis;
  private final LongSupplier clock;
  private final Object lock = new Object();
  private final List<VibeEvent> pending = new ArrayList<>();
  private final StringBuilder deltaText = new StringBuilder();
  private String deltaRunId;
  private long deltaSeq;
  private boolean scheduled;
  private boolean closed;
  private long lastDeliveryAt;

  EventBatcher(MainThreadPoster poster, Sink sink, long minIntervalMillis, LongSupplier clock) {
    this.poster = poster;
    this.sink = sink;
    this.minIntervalMillis = minIntervalMillis;
    this.clock = clock;
    this.lastDeliveryAt = clock.getAsLong() - minIntervalMillis;
  }

  /** Queues {@code event}; text deltas are throttled, every other event is delivered promptly. */
  void offer(@NonNull VibeEvent event) {
    boolean urgent = !(event instanceof VibeEvent.MessageDelta);
    synchronized (lock) {
      if (closed) {
        return;
      }
      if (event instanceof VibeEvent.MessageDelta) {
        VibeEvent.MessageDelta delta = (VibeEvent.MessageDelta) event;
        deltaRunId = delta.runId();
        deltaSeq = delta.seq();
        deltaText.append(delta.text());
      } else {
        moveDeltaToPending();
        pending.add(event);
      }
      if (!scheduled) {
        scheduled = true;
        long wait = urgent ? 0 : Math.max(0, minIntervalMillis - (clock.getAsLong() - lastDeliveryAt));
        poster.post(this::drain, wait);
      } else if (urgent) {
        poster.post(this::drain, 0);
      }
    }
  }

  /** Delivers everything queued as soon as possible, ignoring the rate limit. */
  void flushNow() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      poster.post(this::drain, 0);
    }
  }

  /** Discards undelivered events and refuses new ones; the sink is not called again. */
  void close() {
    synchronized (lock) {
      closed = true;
      pending.clear();
      deltaText.setLength(0);
    }
  }

  private void moveDeltaToPending() {
    if (deltaText.length() > 0) {
      pending.add(new VibeEvent.MessageDelta(deltaRunId, deltaSeq, deltaText.toString()));
      deltaText.setLength(0);
    }
  }

  private void drain() {
    List<VibeEvent> batch;
    synchronized (lock) {
      scheduled = false;
      if (closed) {
        return;
      }
      moveDeltaToPending();
      if (pending.isEmpty()) {
        return;
      }
      batch = Collections.unmodifiableList(new ArrayList<>(pending));
      pending.clear();
      lastDeliveryAt = clock.getAsLong();
    }
    sink.deliver(batch);
  }
}

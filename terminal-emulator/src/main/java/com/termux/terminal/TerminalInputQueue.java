package com.termux.terminal;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * FIFO of user input on its way from the main thread to the PTY writer thread.
 *
 * <p>{@link #offer(byte[], int, int)} never blocks. The previous fixed 4 KB {@link ByteQueue}
 * blocked the main thread as soon as the shell stopped reading stdin (for example while apt
 * downloads) and the kernel PTY buffer was full: the UI froze, stopped draining output, and the
 * output-blocked child could then never read its input again. Pending input is capped at {@link
 * #MAX_PENDING_BYTES}; beyond that {@link #offer} rejects the write so the caller can report it.
 */
final class TerminalInputQueue {

  /** Upper bound of input bytes waiting for the shell to read them. */
  static final int MAX_PENDING_BYTES = 1024 * 1024;

  private final ArrayDeque<byte[]> mChunks = new ArrayDeque<>();
  private int mPendingBytes;
  private boolean mOpen = true;

  /**
   * Appends a copy of {@code data[offset, offset + count)} without blocking.
   *
   * @return false if the queue is closed or the bytes would exceed {@link #MAX_PENDING_BYTES}
   */
  synchronized boolean offer(byte[] data, int offset, int count) {
    if (count <= 0) return true;
    if (!mOpen || mPendingBytes + count > MAX_PENDING_BYTES) return false;
    mChunks.addLast(Arrays.copyOfRange(data, offset, offset + count));
    mPendingBytes += count;
    notifyAll();
    return true;
  }

  /**
   * Blocks until input is available and returns the oldest chunk.
   *
   * @return the chunk, or null once the queue is closed or the waiting thread is interrupted
   */
  synchronized byte[] take() {
    while (mChunks.isEmpty() && mOpen) {
      try {
        wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    if (!mOpen) return null;
    byte[] chunk = mChunks.pollFirst();
    mPendingBytes -= chunk.length;
    return chunk;
  }

  synchronized int pendingBytes() {
    return mPendingBytes;
  }

  synchronized void close() {
    mOpen = false;
    mChunks.clear();
    mPendingBytes = 0;
    notifyAll();
  }
}

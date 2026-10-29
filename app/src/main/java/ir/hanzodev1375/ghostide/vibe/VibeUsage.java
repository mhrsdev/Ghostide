package ir.hanzodev1375.ghostide.vibe;

/** Token counters of a finished run as reported by Hermes; fields it did not report are 0. */
public final class VibeUsage {

  /** A usage object with all counters zero. */
  public static final VibeUsage NONE = new VibeUsage(0, 0, 0, 0, 0);

  private final long inputTokens;
  private final long outputTokens;
  private final long totalTokens;
  private final long cacheReadTokens;
  private final long cacheWriteTokens;

  VibeUsage(
      long inputTokens,
      long outputTokens,
      long totalTokens,
      long cacheReadTokens,
      long cacheWriteTokens) {
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.totalTokens = totalTokens;
    this.cacheReadTokens = cacheReadTokens;
    this.cacheWriteTokens = cacheWriteTokens;
  }

  public long getInputTokens() {
    return inputTokens;
  }

  public long getOutputTokens() {
    return outputTokens;
  }

  public long getTotalTokens() {
    return totalTokens;
  }

  public long getCacheReadTokens() {
    return cacheReadTokens;
  }

  public long getCacheWriteTokens() {
    return cacheWriteTokens;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof VibeUsage)) {
      return false;
    }
    VibeUsage that = (VibeUsage) other;
    return inputTokens == that.inputTokens
        && outputTokens == that.outputTokens
        && totalTokens == that.totalTokens
        && cacheReadTokens == that.cacheReadTokens
        && cacheWriteTokens == that.cacheWriteTokens;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(inputTokens * 31 + outputTokens * 17 + totalTokens);
  }

  @Override
  public String toString() {
    return "VibeUsage{in=" + inputTokens + ", out=" + outputTokens + ", total=" + totalTokens + "}";
  }
}

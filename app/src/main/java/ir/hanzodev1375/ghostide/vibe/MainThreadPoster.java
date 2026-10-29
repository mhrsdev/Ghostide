package ir.hanzodev1375.ghostide.vibe;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

/** Runs work on the main thread, optionally after a delay. Abstracted so the logic is testable. */
interface MainThreadPoster {

  /** Posts {@code work} to run on the main thread after {@code delayMillis} (0 = as soon as possible). */
  void post(@NonNull Runnable work, long delayMillis);

  /** The production implementation backed by the main looper. */
  static MainThreadPoster forMainLooper() {
    Handler handler = new Handler(Looper.getMainLooper());
    return (work, delayMillis) -> {
      if (delayMillis <= 0) {
        handler.post(work);
      } else {
        handler.postDelayed(work, delayMillis);
      }
    };
  }
}

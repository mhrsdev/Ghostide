package ir.hanzodev1375.ghostide.postman.util;

import android.content.Context;
import android.text.format.DateUtils;
import ir.hanzodev1375.ghostide.R;
import java.util.Locale;

public class TimeUtils {

    /**
     * A short, localized "how long ago" label: "Just now" under a minute, then relative minutes,
     * hours and days, and a plain date after a week.
     */
    public static String relativeTime(Context context, long timestampMillis) {
        long now = System.currentTimeMillis();
        if (now - timestampMillis < DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.postman_just_now);
        }
        if (now - timestampMillis >= DateUtils.WEEK_IN_MILLIS) {
            return DateUtils.formatDateTime(
                    context,
                    timestampMillis,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
        }
        return DateUtils.getRelativeTimeSpanString(
                        timestampMillis,
                        now,
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE)
                .toString();
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
    }

    public static String formatDuration(long ms) {
        if (ms < 1000) return ms + " ms";
        return String.format(Locale.US, "%.2f s", ms / 1000.0);
    }
}

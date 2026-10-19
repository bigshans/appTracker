package dev.aerian.apptracker.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Display-only filters; stored data is never modified. */
public final class DisplayPrefs {

    private static final String PREFS = "display";
    private static final String KEY_MIN_DURATION = "min_duration_ms";
    public static final long DEFAULT_MIN_DURATION_MS = 5_000L;

    public static final long[] OPTIONS = {
            0L, 5_000L, 10_000L, 30_000L, 60_000L, 180_000L
    };

    private DisplayPrefs() {
    }

    public static long getMinDuration(Context context) {
        return prefs(context).getLong(KEY_MIN_DURATION, DEFAULT_MIN_DURATION_MS);
    }

    public static void setMinDuration(Context context, long value) {
        prefs(context).edit().putLong(KEY_MIN_DURATION, value).apply();
    }

    public static String label(long millis) {
        if (millis <= 0) {
            return "不隐藏";
        }
        if (millis < 60_000L) {
            return (millis / 1_000L) + " 秒";
        }
        return (millis / 60_000L) + " 分钟";
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

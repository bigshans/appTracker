package dev.aerian.apptracker.data;

import android.content.Context;
import android.content.SharedPreferences;

/** Display-only filters; stored data is never modified. */
public final class DisplayPrefs {

    private static final String PREFS = "display";
    private static final String KEY_MIN_DURATION = "min_duration_ms";
    private static final String KEY_HISTORY_START = "history_start_minute";
    private static final String KEY_HISTORY_END = "history_end_minute";
    private static final String KEY_APP_DETAIL_START = "app_detail_start_minute";
    private static final String KEY_APP_DETAIL_END = "app_detail_end_minute";
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

    public static boolean hasHistoryTimeRange(Context context) {
        return prefs(context).getInt(KEY_HISTORY_START, -1) >= 0
                && prefs(context).getInt(KEY_HISTORY_END, -1) >= 0;
    }

    public static int getHistoryStartMinute(Context context) {
        return prefs(context).getInt(KEY_HISTORY_START, -1);
    }

    public static int getHistoryEndMinute(Context context) {
        return prefs(context).getInt(KEY_HISTORY_END, -1);
    }

    public static void setHistoryTimeRange(Context context, int startMinute, int endMinute) {
        prefs(context).edit()
                .putInt(KEY_HISTORY_START, startMinute)
                .putInt(KEY_HISTORY_END, endMinute)
                .apply();
    }

    public static void clearHistoryTimeRange(Context context) {
        prefs(context).edit()
                .remove(KEY_HISTORY_START)
                .remove(KEY_HISTORY_END)
                .apply();
    }

    public static int getAppDetailStartMinute(Context context) {
        return prefs(context).getInt(KEY_APP_DETAIL_START, -1);
    }

    public static int getAppDetailEndMinute(Context context) {
        return prefs(context).getInt(KEY_APP_DETAIL_END, -1);
    }

    public static void setAppDetailTimeRange(Context context, int startMinute, int endMinute) {
        prefs(context).edit()
                .putInt(KEY_APP_DETAIL_START, startMinute)
                .putInt(KEY_APP_DETAIL_END, endMinute)
                .apply();
    }

    public static void clearAppDetailTimeRange(Context context) {
        prefs(context).edit()
                .remove(KEY_APP_DETAIL_START)
                .remove(KEY_APP_DETAIL_END)
                .apply();
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

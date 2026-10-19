package dev.aerian.apptracker.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class TimeUtils {

    private TimeUtils() {
    }

    /** "1小时05分", "12分30秒", "42秒" */
    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return "0秒";
        }
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "小时" + String.format(Locale.CHINA, "%02d", minutes) + "分";
        }
        if (minutes > 0) {
            return minutes + "分" + String.format(Locale.CHINA, "%02d", seconds) + "秒";
        }
        return seconds + "秒";
    }

    /** Compact duration for ranking rows: "1小时23分", "45分钟", "<1分钟" */
    public static String formatCompact(long millis) {
        if (millis <= 0) {
            return "0分钟";
        }
        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (totalMinutes < 1) {
            return "<1分钟";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) {
            return minutes == 0 ? hours + "小时" : hours + "小时" + minutes + "分";
        }
        return minutes + "分钟";
    }

    /** HH:mm:ss for the live timer. */
    public static String formatClockTimer(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.CHINA, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    /** HH:mm */
    public static String formatTimeOfDay(long millis) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
    }

    /** HH:mm:ss */
    public static String formatTimeOfDaySeconds(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }

    /** yyyy-MM-dd HH:mm:ss (also used for CSV export) */
    public static String formatDateTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(millis));
    }
}

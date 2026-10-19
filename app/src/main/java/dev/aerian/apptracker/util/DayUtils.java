package dev.aerian.apptracker.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class DayUtils {

    private DayUtils() {
    }

    public static long dayStart(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long dayEnd(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static LocalDate dateOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}

package dev.aerian.apptracker.data;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.aerian.apptracker.util.AppUtils;
import dev.aerian.apptracker.util.DayUtils;

/**
 * Rebuilds daily foreground durations and intervals from Android's UsageStats
 * events. This runs only during an on-demand sync (app open / once per day),
 * never as a continuous poller.
 */
public final class UsageSync {

    private static final String PREFS = "usage_sync";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static final long MIN_DURATION_MS = 1_000L;
    private static final long MERGE_GAP_MS = 5_000L;

    private UsageSync() {
    }

    public static long getLastSync(Context context) {
        return prefs(context).getLong(KEY_LAST_SYNC, 0L);
    }

    public static void syncRecentDays(Context context, int days) {
        Context appContext = context.getApplicationContext();
        if (!AppUtils.isUsageAccessGranted(appContext)) {
            return;
        }
        UsageStatsManager manager =
                (UsageStatsManager) appContext.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return;
        }

        UsageStore store = new UsageStore(appContext);
        try {
            long now = System.currentTimeMillis();
            int count = Math.max(1, days);
            for (int i = 0; i < count; i++) {
                LocalDate date = LocalDate.now().minusDays(i);
                long start = DayUtils.dayStart(date);
                long end = Math.min(DayUtils.dayEnd(date), now);
                DayResult result = queryDay(appContext, manager, start, end, now);
                if (result.isEmpty() && i > 0) {
                    continue;
                }
                store.replaceDay(date.toEpochDay(), result.aggregates, result.intervals);
            }
            prefs(appContext).edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
        } finally {
            store.close();
        }
    }

    private static DayResult queryDay(Context context, UsageStatsManager manager,
                                      long start, long end, long now) {
        Map<String, Long> durations = new HashMap<>();
        List<UsageInterval> intervals = new ArrayList<>();
        UsageEvents events = manager.queryEvents(start - 24L * 60L * 60L * 1000L, end);
        if (events != null) {
            String currentPackage = null;
            String currentLabel = null;
            long currentSince = 0L;
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.getNextEvent(event)) {
                int type = event.getEventType();
                String pkg = event.getPackageName();
                long timestamp = event.getTimeStamp();

                if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (pkg == null || pkg.isEmpty()) {
                        continue;
                    }
                    int kind = AppUtils.classify(context, pkg);
                    if (kind == AppUtils.TYPE_IGNORE) {
                        continue;
                    }
                    if (kind == AppUtils.TYPE_CLOSE) {
                        if (currentPackage != null) {
                            closeInterval(context, durations, intervals, currentPackage,
                                    currentLabel, currentSince, timestamp, start, end, false);
                            currentPackage = null;
                            currentLabel = null;
                        }
                        continue;
                    }
                    if (pkg.equals(currentPackage)) {
                        continue;
                    }
                    if (currentPackage != null) {
                        closeInterval(context, durations, intervals, currentPackage,
                                currentLabel, currentSince, timestamp, start, end, false);
                    }
                    currentPackage = pkg;
                    currentLabel = AppLabelResolver.label(context, pkg);
                    currentSince = timestamp;
                } else if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (pkg != null && pkg.equals(currentPackage)) {
                        closeInterval(context, durations, intervals, currentPackage,
                                currentLabel, currentSince, timestamp, start, end, false);
                        currentPackage = null;
                        currentLabel = null;
                    }
                } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                        || type == UsageEvents.Event.KEYGUARD_SHOWN
                        || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                    if (currentPackage != null) {
                        closeInterval(context, durations, intervals, currentPackage,
                                currentLabel, currentSince, timestamp, start, end, false);
                        currentPackage = null;
                        currentLabel = null;
                    }
                }
            }
            if (currentPackage != null) {
                boolean ongoing = end >= now - 1_000L;
                closeInterval(context, durations, intervals, currentPackage, currentLabel,
                        currentSince, Math.min(end, now), start, end, ongoing);
            }
        }

        List<AppUsage> aggregates = new ArrayList<>();
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            if (entry.getValue() >= MIN_DURATION_MS) {
                aggregates.add(new AppUsage(entry.getKey(),
                        AppLabelResolver.label(context, entry.getKey()), entry.getValue()));
            }
        }
        aggregates.sort((a, b) -> Long.compare(b.durationMs, a.durationMs));
        intervals.sort((a, b) -> Long.compare(a.startTime, b.startTime));
        return new DayResult(aggregates, intervals);
    }

    private static void closeInterval(Context context, Map<String, Long> durations,
                                      List<UsageInterval> intervals, String pkg, String label,
                                      long from, long to, long dayStart, long dayEnd,
                                      boolean ongoing) {
        long start = Math.max(from, dayStart);
        long end = Math.min(to, dayEnd);
        long duration = end - start;
        if (duration < MIN_DURATION_MS) {
            return;
        }
        Long previous = durations.get(pkg);
        durations.put(pkg, previous == null ? duration : previous + duration);

        String appLabel = label != null ? label : AppLabelResolver.label(context, pkg);
        if (!ongoing && !intervals.isEmpty()) {
            UsageInterval last = intervals.get(intervals.size() - 1);
            if (!last.ongoing && last.packageName.equals(pkg)
                    && start >= last.endTime
                    && start - last.endTime <= MERGE_GAP_MS) {
                intervals.set(intervals.size() - 1, new UsageInterval(pkg, appLabel,
                        last.startTime, end, false));
                return;
            }
        }
        intervals.add(new UsageInterval(pkg, appLabel, start, end, ongoing));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static final class DayResult {
        final List<AppUsage> aggregates;
        final List<UsageInterval> intervals;

        DayResult(List<AppUsage> aggregates, List<UsageInterval> intervals) {
            this.aggregates = aggregates;
            this.intervals = intervals;
        }

        boolean isEmpty() {
            return aggregates.isEmpty() && intervals.isEmpty();
        }
    }
}

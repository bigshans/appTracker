package dev.aerian.apptracker.data;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Schedules low-frequency background syncs; no foreground service is used. */
public final class SyncScheduler {

    private static final String PERIODIC_WORK = "usage-sync-periodic";
    private static final String ONCE_WORK = "usage-sync-now";

    private SyncScheduler() {
    }

    public static void schedulePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                UsageSyncWorker.class, 12, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(PERIODIC_WORK,
                        ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    /** Runs one sync as soon as WorkManager allows, e.g. after boot. */
    public static void syncNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UsageSyncWorker.class)
                .build();
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(ONCE_WORK, ExistingWorkPolicy.REPLACE, request);
    }
}

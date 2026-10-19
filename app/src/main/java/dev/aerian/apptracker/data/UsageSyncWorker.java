package dev.aerian.apptracker.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import dev.aerian.apptracker.util.AppUtils;

/**
 * Once-a-day snapshot. The actual foreground duration is accumulated by the
 * Android framework, so this worker only copies aggregates into local storage.
 */
public class UsageSyncWorker extends Worker {

    public UsageSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!AppUtils.isUsageAccessGranted(context)) {
            return Result.success();
        }
        UsageSync.syncRecentDays(context, 14);
        return Result.success();
    }
}

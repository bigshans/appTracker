package dev.aerian.apptracker.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import dev.aerian.apptracker.data.SyncScheduler;

/** Re-arms the background sync after a reboot. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        SyncScheduler.schedulePeriodic(context);
        SyncScheduler.syncNow(context);
    }
}

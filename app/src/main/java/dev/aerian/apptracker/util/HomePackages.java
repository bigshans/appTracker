package dev.aerian.apptracker.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detects home/launcher packages. All launchers are excluded from statistics,
 * not just the current default one, because users can switch launchers and
 * OEMs ship multiple launcher packages.
 */
public final class HomePackages {

    private static Set<String> cached;

    private HomePackages() {
    }

    public static boolean isHome(Context context, String packageName) {
        return packageName != null && packages(context).contains(packageName);
    }

    public static synchronized Set<String> packages(Context context) {
        if (cached != null) {
            return cached;
        }
        Set<String> result = new HashSet<>();
        try {
            PackageManager pm = context.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> resolved = pm.queryIntentActivities(home, 0);
            for (ResolveInfo info : resolved) {
                ActivityInfo activity = info.activityInfo;
                if (activity == null || activity.packageName == null) {
                    continue;
                }
                // Settings exposes a FallbackHome activity before the user
                // unlocks; excluding the whole package would hide Settings.
                if ("com.android.settings".equals(activity.packageName)
                        || (activity.name != null && activity.name.endsWith("FallbackHome"))) {
                    continue;
                }
                result.add(activity.packageName);
            }
            if (result.isEmpty()) {
                ResolveInfo fallback = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
                if (fallback != null && fallback.activityInfo != null) {
                    result.add(fallback.activityInfo.packageName);
                }
            }
        } catch (Exception ignored) {
            // Fall through with an empty set.
        }
        cached = result;
        return cached;
    }
}

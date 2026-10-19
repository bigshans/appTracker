package dev.aerian.apptracker.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.Map;

public final class AppLabelResolver {

    private static final Map<String, String> CACHE = new HashMap<>();

    private AppLabelResolver() {
    }

    public static synchronized String label(Context context, String packageName) {
        if (packageName == null) {
            return "未知应用";
        }
        String cached = CACHE.get(packageName);
        if (cached != null) {
            return cached;
        }
        String label;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence value = pm.getApplicationLabel(info);
            label = value == null ? packageName : value.toString();
        } catch (Exception e) {
            label = packageName;
        }
        CACHE.put(packageName, label);
        return label;
    }
}

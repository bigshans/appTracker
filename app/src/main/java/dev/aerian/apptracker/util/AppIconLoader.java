package dev.aerian.apptracker.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

public final class AppIconLoader {

    private final PackageManager packageManager;
    private final Drawable fallback;
    private final LruCache<String, Drawable> cache = new LruCache<>(128);

    public AppIconLoader(Context context) {
        this.packageManager = context.getApplicationContext().getPackageManager();
        this.fallback = packageManager.getDefaultActivityIcon();
    }

    public Drawable load(String packageName) {
        if (packageName == null) {
            return fallback;
        }
        Drawable cached = cache.get(packageName);
        if (cached != null) {
            return cached;
        }
        Drawable drawable;
        try {
            drawable = packageManager.getApplicationIcon(packageName);
        } catch (Exception e) {
            drawable = fallback;
        }
        cache.put(packageName, drawable);
        return drawable;
    }
}

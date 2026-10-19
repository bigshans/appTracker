package dev.aerian.apptracker.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Packages that should never be recorded. Stored in SharedPreferences so the
 * tracker can check it without touching the database on every window change.
 */
public final class BlacklistStore {

    private static final String PREFS = "blacklist";
    private static final String KEY_PACKAGES = "packages";

    private BlacklistStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isBlacklisted(Context context, String packageName) {
        return packageName != null
                && prefs(context).getStringSet(KEY_PACKAGES, new HashSet<>())
                .contains(packageName);
    }

    public static Set<String> getAll(Context context) {
        return new HashSet<>(prefs(context)
                .getStringSet(KEY_PACKAGES, new HashSet<>()));
    }

    public static int count(Context context) {
        return prefs(context).getStringSet(KEY_PACKAGES, new HashSet<>()).size();
    }

    public static void setBlacklisted(Context context, String packageName, boolean blacklisted) {
        if (packageName == null) {
            return;
        }
        SharedPreferences preferences = prefs(context);
        Set<String> current = preferences.getStringSet(KEY_PACKAGES, new HashSet<>());
        Set<String> updated = new HashSet<>(current);
        if (blacklisted) {
            updated.add(packageName);
        } else {
            updated.remove(packageName);
        }
        preferences.edit().putStringSet(KEY_PACKAGES, updated).apply();
    }
}

package dev.aerian.apptracker.util;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Process;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class AppUtils {

    public static final int TYPE_TRACK = 0;
    public static final int TYPE_CLOSE = 1;
    public static final int TYPE_IGNORE = 2;

    private static Set<String> imePackages;

    private AppUtils() {
    }

    /**
     * Classifies a foreground package:
     * TRACK  - start/continue a tracked session
     * CLOSE  - the user left to home or opened this tracker, end the session
     * IGNORE - transient system surfaces (notification shade, keyboard, dialogs)
     */
    public static int classify(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return TYPE_IGNORE;
        }
        if (packageName.equals(context.getPackageName())) {
            return TYPE_CLOSE;
        }
        if (isHomePackage(context, packageName)) {
            return TYPE_CLOSE;
        }
        if (isIgnoredPackage(context, packageName)) {
            return TYPE_IGNORE;
        }
        return TYPE_TRACK;
    }

    private static boolean isIgnoredPackage(Context context, String packageName) {
        switch (packageName) {
            case "com.android.systemui":
            case "android":
            case "com.android.permissioncontroller":
            case "com.google.android.permissioncontroller":
            case "com.android.keyguard":
            case "com.google.android.inputmethod.latin":
            case "com.sohu.inputmethod.sogou":
            case "com.baidu.input":
            case "com.iflytek.inputmethod":
                return true;
            default:
                break;
        }
        return enabledInputMethodPackages(context).contains(packageName);
    }

    private static synchronized Set<String> enabledInputMethodPackages(Context context) {
        if (imePackages == null) {
            Set<String> packages = new HashSet<>();
            try {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    List<InputMethodInfo> methods = imm.getEnabledInputMethodList();
                    for (InputMethodInfo method : methods) {
                        packages.add(method.getPackageName());
                    }
                }
            } catch (Exception ignored) {
                // Fall through with an empty set.
            }
            imePackages = packages;
        }
        return imePackages;
    }

    public static boolean isHomePackage(Context context, String packageName) {
        return HomePackages.isHome(context, packageName);
    }

    public static boolean isUsageAccessGranted(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static void openUsageAccessSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            // Nothing else we can do.
        }
    }
}

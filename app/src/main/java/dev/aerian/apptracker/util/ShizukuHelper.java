package dev.aerian.apptracker.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Thin wrapper around the Shizuku API. Shizuku is optional; when available it
 * can grant usage access with shell privileges so the polling service works
 * without the accessibility service.
 */
public final class ShizukuHelper {

    public static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    private ShizukuHelper() {
    }

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean hasPermission() {
        try {
            return isRunning()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void requestPermission(int requestCode) {
        try {
            Shizuku.requestPermission(requestCode);
        } catch (Throwable ignored) {
            // Shizuku is not available.
        }
    }

    /**
     * Grants usage access through the shell app-op. Some Android 16/OEM builds
     * no longer allow shell to change this op; callers should ask the user to
     * grant usage access manually in system settings.
     */
    public static boolean grantUsageAccess(Context context) {
        if (!hasPermission()) {
            return false;
        }
        String packageName = context.getPackageName();
        ShellResult result = runShell("appops set " + packageName + " GET_USAGE_STATS allow");
        if (result.exitCode != 0) {
            return false;
        }
        try {
            Thread.sleep(250L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return AppUtils.isUsageAccessGranted(context);
    }

    public static void openShizuku(Context context) {
        Intent launch = context.getPackageManager()
                .getLaunchIntentForPackage(SHIZUKU_PACKAGE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(launch);
            } catch (Exception ignored) {
                // Shizuku is installed but cannot be launched.
            }
        }
    }

    public static void openShizukuWebsite(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://shizuku.rikka.app/"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
            // No browser available.
        }
    }

    private static ShellResult runShell(String command) {
        IRemoteProcess process = null;
        try {
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) {
                return new ShellResult(-1, "");
            }
            process = service.newProcess(new String[]{"sh", "-c", command}, null, null);
            if (process == null) {
                return new ShellResult(-1, "");
            }
            String output = readAll(process);
            int exitCode = process.waitFor();
            return new ShellResult(exitCode, output);
        } catch (Throwable e) {
            return new ShellResult(-1, "");
        } finally {
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                    // Already gone.
                }
            }
        }
    }

    private static String readAll(IRemoteProcess process) {
        StringBuilder builder = new StringBuilder();
        ParcelFileDescriptor descriptor = null;
        try {
            descriptor = process.getInputStream();
            if (descriptor == null) {
                return "";
            }
            InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
            }
        } catch (Exception ignored) {
            // Output is only used for diagnostics.
        } finally {
            if (descriptor != null) {
                try {
                    descriptor.close();
                } catch (Exception ignored) {
                    // Already closed.
                }
            }
        }
        return builder.toString();
    }

    private static final class ShellResult {
        final int exitCode;
        final String output;

        ShellResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}

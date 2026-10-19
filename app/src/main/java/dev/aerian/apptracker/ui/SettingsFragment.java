package dev.aerian.apptracker.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.BlacklistStore;
import dev.aerian.apptracker.data.UsageInterval;
import dev.aerian.apptracker.data.UsageStore;
import dev.aerian.apptracker.data.UsageSync;
import dev.aerian.apptracker.util.AppUtils;
import dev.aerian.apptracker.util.DayUtils;
import dev.aerian.apptracker.util.ShizukuHelper;
import dev.aerian.apptracker.util.TimeUtils;
import rikka.shizuku.Shizuku;

public class SettingsFragment extends Fragment {

    private static final int SHIZUKU_REQUEST_CODE = 2001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView usageStatus;
    private TextView shizukuStatus;
    private TextView shizukuHint;
    private TextView sessionCount;
    private TextView lastSyncView;
    private TextView blacklistCount;
    private TextView progressLabel;
    private LinearProgressIndicator progress;
    private MaterialButton usageButton;
    private MaterialButton shizukuButton;
    private MaterialButton syncButton;
    private MaterialButton exportButton;
    private MaterialButton clearButton;

    private boolean busy;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_REQUEST_CODE) {
                    return;
                }
                mainHandler.post(() -> {
                    if (!isAdded() || getView() == null) {
                        return;
                    }
                    updatePermissionStatus();
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        grantUsageAccessViaShizuku();
                    }
                });
            };

    private final ActivityResultLauncher<String> createDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri != null) {
                    writeCsv(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        usageStatus = view.findViewById(R.id.tv_usage_status);
        shizukuStatus = view.findViewById(R.id.tv_shizuku_status);
        shizukuHint = view.findViewById(R.id.tv_shizuku_hint);
        sessionCount = view.findViewById(R.id.tv_session_count);
        lastSyncView = view.findViewById(R.id.tv_last_sync);
        blacklistCount = view.findViewById(R.id.tv_blacklist_count);
        progress = view.findViewById(R.id.progress);
        progressLabel = view.findViewById(R.id.tv_progress_label);
        usageButton = view.findViewById(R.id.btn_usage_access);
        shizukuButton = view.findViewById(R.id.btn_shizuku);
        syncButton = view.findViewById(R.id.btn_sync);
        exportButton = view.findViewById(R.id.btn_export);
        clearButton = view.findViewById(R.id.btn_clear);

        try {
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable ignored) {
            // Shizuku is unavailable.
        }

        usageButton.setOnClickListener(v -> AppUtils.openUsageAccessSettings(requireContext()));
        shizukuButton.setOnClickListener(v -> onShizukuClicked());
        syncButton.setOnClickListener(v -> syncNow());
        exportButton.setOnClickListener(v -> exportCsv());
        clearButton.setOnClickListener(v -> confirmClear());
        view.findViewById(R.id.btn_blacklist).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BlacklistActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePermissionStatus();
        updateCount();
        updateBlacklistCount();
        updateLastSync();
    }

    @Override
    public void onDestroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        } catch (Throwable ignored) {
            // Shizuku is unavailable.
        }
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void updatePermissionStatus() {
        if (!isAdded() || getView() == null) {
            return;
        }
        Context context = requireContext();
        boolean usage = AppUtils.isUsageAccessGranted(context);
        usageStatus.setText(usage ? R.string.settings_enabled : R.string.settings_disabled);
        usageStatus.setTextColor(ContextCompat.getColor(context,
                usage ? R.color.success : R.color.error));
        usageButton.setVisibility(usage ? View.GONE : View.VISIBLE);
        updateShizukuStatus(context, usage);
    }

    private void updateShizukuStatus(Context context, boolean usage) {
        boolean installed = ShizukuHelper.isInstalled(context);
        boolean running = installed && ShizukuHelper.isRunning();
        boolean granted = running && ShizukuHelper.hasPermission();

        shizukuButton.setEnabled(true);
        if (!installed) {
            shizukuStatus.setText(R.string.settings_shizuku_not_installed);
            shizukuStatus.setTextColor(ContextCompat.getColor(context, R.color.error));
            shizukuHint.setText(R.string.settings_shizuku_hint_install);
            shizukuButton.setText(R.string.settings_shizuku_install);
        } else if (!running) {
            shizukuStatus.setText(R.string.settings_shizuku_not_running);
            shizukuStatus.setTextColor(ContextCompat.getColor(context, R.color.error));
            shizukuHint.setText(R.string.settings_shizuku_hint_start);
            shizukuButton.setText(R.string.settings_shizuku_open);
        } else if (!granted) {
            shizukuStatus.setText(R.string.settings_shizuku_not_granted);
            shizukuStatus.setTextColor(ContextCompat.getColor(context, R.color.error));
            shizukuHint.setText(R.string.settings_shizuku_hint_grant);
            shizukuButton.setText(R.string.settings_shizuku_grant);
        } else if (!usage) {
            shizukuStatus.setText(R.string.settings_shizuku_granted);
            shizukuStatus.setTextColor(ContextCompat.getColor(context, R.color.success));
            shizukuHint.setText(R.string.settings_shizuku_hint_usage);
            shizukuButton.setText(R.string.settings_shizuku_grant_usage);
        } else {
            shizukuStatus.setText(R.string.settings_shizuku_ready);
            shizukuStatus.setTextColor(ContextCompat.getColor(context, R.color.success));
            shizukuHint.setText(R.string.settings_shizuku_hint_ready);
            shizukuButton.setText(R.string.settings_shizuku_ready);
            shizukuButton.setEnabled(false);
        }
        shizukuButton.setEnabled(shizukuButton.isEnabled() && !busy);
    }

    private void onShizukuClicked() {
        if (busy || !isAdded()) {
            return;
        }
        Context context = requireContext();
        if (!ShizukuHelper.isInstalled(context)) {
            ShizukuHelper.openShizukuWebsite(context);
            return;
        }
        if (!ShizukuHelper.isRunning()) {
            ShizukuHelper.openShizuku(context);
            return;
        }
        if (!ShizukuHelper.hasPermission()) {
            ShizukuHelper.requestPermission(SHIZUKU_REQUEST_CODE);
            Toast.makeText(context, R.string.settings_shizuku_request, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AppUtils.isUsageAccessGranted(context)) {
            grantUsageAccessViaShizuku();
        }
    }

    private void grantUsageAccessViaShizuku() {
        if (busy || !isAdded()) {
            return;
        }
        setBusy(true);
        showProgress(getString(R.string.settings_shizuku_busy));
        Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            boolean success = ShizukuHelper.grantUsageAccess(appContext);
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setBusy(false);
                hideProgress();
                if (success) {
                    Toast.makeText(requireContext(), R.string.settings_shizuku_success,
                            Toast.LENGTH_SHORT).show();
                    updatePermissionStatus();
                    syncNow();
                } else {
                    updatePermissionStatus();
                    showShizukuRestrictedDialog();
                }
            });
        });
    }

    private void showShizukuRestrictedDialog() {
        if (!isAdded() || getView() == null) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_shizuku_restricted_title)
                .setMessage(R.string.settings_shizuku_restricted_message)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_open_settings,
                        (dialog, which) -> AppUtils.openUsageAccessSettings(requireContext()))
                .show();
    }

    private void syncNow() {
        if (busy || !isAdded()) {
            return;
        }
        Context context = requireContext();
        if (!AppUtils.isUsageAccessGranted(context)) {
            Toast.makeText(context, R.string.settings_sync_need_access, Toast.LENGTH_SHORT).show();
            AppUtils.openUsageAccessSettings(context);
            return;
        }
        setBusy(true);
        showProgress(getString(R.string.sync_syncing));
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            UsageSync.syncRecentDays(appContext, 14);
            UsageStore store = new UsageStore(appContext);
            int count;
            try {
                count = store.getDay(LocalDate.now().toEpochDay()).size();
            } finally {
                store.close();
            }
            final int appCount = count;
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setBusy(false);
                hideProgress();
                Toast.makeText(requireContext(),
                        getString(R.string.settings_sync_done, appCount),
                        Toast.LENGTH_SHORT).show();
                updateCount();
                updateLastSync();
            });
        });
    }

    private void updateCount() {
        if (!isAdded() || getView() == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            UsageStore store = new UsageStore(appContext);
            int count;
            try {
                count = store.countIntervals();
            } finally {
                store.close();
            }
            final int rows = count;
            mainHandler.post(() -> {
                if (isAdded() && getView() != null) {
                    sessionCount.setText(getString(R.string.settings_apps_count, rows));
                }
            });
        });
    }

    private void updateLastSync() {
        if (!isAdded() || getView() == null) {
            return;
        }
        long lastSync = UsageSync.getLastSync(requireContext());
        if (lastSync <= 0) {
            lastSyncView.setText(R.string.sync_never);
        } else {
            String time = DayUtils.dateOf(lastSync).equals(LocalDate.now())
                    ? TimeUtils.formatTimeOfDay(lastSync)
                    : TimeUtils.formatDateTime(lastSync);
            lastSyncView.setText(getString(R.string.sync_status, time));
        }
    }

    private void updateBlacklistCount() {
        if (!isAdded() || getView() == null) {
            return;
        }
        blacklistCount.setText(getString(R.string.settings_blacklist_count,
                BlacklistStore.count(requireContext())));
    }

    private void exportCsv() {
        if (busy || !isAdded()) {
            return;
        }
        setBusy(true);
        showProgress(getString(R.string.settings_exporting));
        Context appContext = requireContext().getApplicationContext();
        executor.execute(() -> {
            UsageStore store = new UsageStore(appContext);
            int count;
            try {
                count = store.getAllIntervals().size();
            } finally {
                store.close();
            }
            final int rows = count;
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setBusy(false);
                hideProgress();
                if (rows == 0) {
                    Toast.makeText(requireContext(), R.string.settings_export_empty,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                createDocumentLauncher.launch("app-usage-" + LocalDate.now() + ".csv");
            });
        });
    }

    private void writeCsv(Uri uri) {
        if (!isAdded()) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        setBusy(true);
        showProgress(getString(R.string.settings_exporting));
        executor.execute(() -> {
            int exported = 0;
            String error = null;
            UsageStore store = new UsageStore(appContext);
            try {
                List<UsageInterval> rows = store.getAllIntervals();
                try (OutputStreamWriter writer = new OutputStreamWriter(
                        appContext.getContentResolver().openOutputStream(uri),
                        StandardCharsets.UTF_8)) {
                    writer.write('\ufeff');
                    writer.write("日期,开始时间,结束时间,应用,包名,时长(秒)\n");
                    for (UsageInterval row : rows) {
                        writer.write(csv(DayUtils.dateOf(row.startTime).toString()));
                        writer.write(",");
                        writer.write(csv(TimeUtils.formatTimeOfDaySeconds(row.startTime)));
                        writer.write(",");
                        writer.write(csv(row.ongoing ? "" : TimeUtils.formatTimeOfDaySeconds(row.endTime)));
                        writer.write(",");
                        writer.write(csv(row.appLabel));
                        writer.write(",");
                        writer.write(csv(row.packageName));
                        writer.write(",");
                        writer.write(String.valueOf(row.durationMs() / 1000L));
                        writer.write("\n");
                        exported++;
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                store.close();
            }

            final int exportedCount = exported;
            final String failure = error;
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                setBusy(false);
                hideProgress();
                if (failure != null) {
                    Toast.makeText(requireContext(),
                            getString(R.string.settings_export_failed, failure),
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(requireContext(),
                            getString(R.string.settings_export_done, exportedCount),
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void confirmClear() {
        if (busy || !isAdded()) {
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_clear)
                .setMessage(R.string.settings_clear_confirm)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_clear_action, (dialog, which) -> {
                    Context appContext = requireContext().getApplicationContext();
                    executor.execute(() -> {
                        UsageStore store = new UsageStore(appContext);
                        try {
                            store.deleteAll();
                        } finally {
                            store.close();
                        }
                        mainHandler.post(() -> {
                            if (!isAdded() || getView() == null) {
                                return;
                            }
                            Toast.makeText(requireContext(), R.string.settings_cleared,
                                    Toast.LENGTH_SHORT).show();
                            updateCount();
                        });
                    });
                })
                .show();
    }

    private void setBusy(boolean value) {
        busy = value;
        syncButton.setEnabled(!value);
        exportButton.setEnabled(!value);
        clearButton.setEnabled(!value);
        shizukuButton.setEnabled(!value);
        if (!value) {
            updatePermissionStatus();
        }
    }

    private void showProgress(String label) {
        progress.setVisibility(View.VISIBLE);
        progressLabel.setText(label);
        progressLabel.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        progress.setVisibility(View.GONE);
        progressLabel.setVisibility(View.GONE);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

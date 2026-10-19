package dev.aerian.apptracker.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.MainActivity;
import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppUsage;
import dev.aerian.apptracker.data.UsageStore;
import dev.aerian.apptracker.data.UsageSync;
import dev.aerian.apptracker.ui.adapter.AppStatAdapter;
import dev.aerian.apptracker.util.AppIconLoader;
import dev.aerian.apptracker.util.AppUtils;
import dev.aerian.apptracker.util.DayUtils;
import dev.aerian.apptracker.util.TimeUtils;

public class OverviewFragment extends Fragment {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MaterialCardView permissionCard;
    private TextView todayTotal;
    private TextView todayApps;
    private TextView todayDate;
    private TextView lastSync;
    private RecyclerView appsRecycler;
    private TextView appsEmpty;
    private MaterialButton syncButton;
    private AppStatAdapter adapter;

    private final Runnable refreshTick = new Runnable() {
        @Override
        public void run() {
            refresh(1);
            ticker.postDelayed(this, 60_000L);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        permissionCard = view.findViewById(R.id.card_permission);
        todayTotal = view.findViewById(R.id.tv_today_total);
        todayApps = view.findViewById(R.id.tv_today_launches);
        todayDate = view.findViewById(R.id.tv_today_date);
        lastSync = view.findViewById(R.id.tv_last_sync);
        appsRecycler = view.findViewById(R.id.rv_apps);
        appsEmpty = view.findViewById(R.id.tv_apps_empty);
        syncButton = view.findViewById(R.id.btn_sync);

        MaterialButton openAccess = view.findViewById(R.id.btn_open_usage_access);
        openAccess.setOnClickListener(v -> AppUtils.openUsageAccessSettings(requireContext()));
        MaterialButton shizukuMode = view.findViewById(R.id.btn_shizuku_mode);
        shizukuMode.setOnClickListener(v -> {
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).selectTab(R.id.nav_settings);
            }
        });
        syncButton.setOnClickListener(v -> refresh(14));

        adapter = new AppStatAdapter(new AppIconLoader(requireContext()), usage ->
                AppDetailActivity.start(requireContext(), usage.packageName,
                        LocalDate.now().toEpochDay()));
        appsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        appsRecycler.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        ticker.removeCallbacks(refreshTick);
        refresh(14);
        ticker.postDelayed(refreshTick, 60_000L);
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(refreshTick);
    }

    @Override
    public void onDestroy() {
        ticker.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }

    private void refresh(int syncDays) {
        if (!isAdded() || getView() == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        boolean hasAccess = AppUtils.isUsageAccessGranted(appContext);
        permissionCard.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
        if (syncDays > 1) {
            syncButton.setEnabled(false);
            syncButton.setText(R.string.sync_syncing);
        }

        final LocalDate today = LocalDate.now();
        final long epochDay = today.toEpochDay();
        executor.execute(() -> {
            if (syncDays > 0 && hasAccess) {
                UsageSync.syncRecentDays(appContext, syncDays);
            }
            UsageStore store = new UsageStore(appContext);
            List<AppUsage> usage;
            try {
                usage = store.getDay(epochDay);
            } finally {
                store.close();
            }
            long total = 0L;
            for (AppUsage item : usage) {
                total += item.durationMs;
            }
            final long totalDuration = total;
            final long lastSyncAt = UsageSync.getLastSync(appContext);
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                render(today, usage, totalDuration, lastSyncAt);
                syncButton.setEnabled(true);
                syncButton.setText(R.string.sync_now);
            });
        });
    }

    private void render(LocalDate today, List<AppUsage> usage, long totalDuration, long lastSyncAt) {
        todayTotal.setText(TimeUtils.formatDuration(totalDuration));
        todayApps.setText(String.valueOf(usage.size()));
        todayDate.setText(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA).format(today));
        if (lastSyncAt <= 0) {
            lastSync.setText(R.string.sync_never);
        } else {
            String time = DayUtils.dateOf(lastSyncAt).equals(LocalDate.now())
                    ? TimeUtils.formatTimeOfDay(lastSyncAt)
                    : TimeUtils.formatDateTime(lastSyncAt);
            lastSync.setText(getString(R.string.sync_status, time));
        }
        adapter.submit(usage, totalDuration);
        boolean empty = usage.isEmpty();
        appsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        appsRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}

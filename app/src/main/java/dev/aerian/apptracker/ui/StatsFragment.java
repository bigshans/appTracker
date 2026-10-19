package dev.aerian.apptracker.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppUsage;
import dev.aerian.apptracker.data.DailyTotal;
import dev.aerian.apptracker.data.DisplayPrefs;
import dev.aerian.apptracker.data.UsageStore;
import dev.aerian.apptracker.data.UsageSync;
import dev.aerian.apptracker.ui.adapter.AppStatAdapter;
import dev.aerian.apptracker.ui.view.DailyBarChartView;
import dev.aerian.apptracker.util.AppIconLoader;
import dev.aerian.apptracker.util.AppUtils;
import dev.aerian.apptracker.util.TimeUtils;

public class StatsFragment extends Fragment {

    private static final int MAX_RANGE_DAYS = 90;
    private static final DateTimeFormatter RANGE_DATE =
            DateTimeFormatter.ofPattern("M月d日", Locale.CHINA);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MaterialButtonToggleGroup rangeGroup;
    private TextView rangeView;
    private TextView totalView;
    private TextView dailyAvgView;
    private TextView appsView;
    private TextView activeDaysView;
    private TextView peakView;
    private TextView filterNote;
    private TextView emptyView;
    private DailyBarChartView chart;
    private RecyclerView recycler;
    private AppStatAdapter adapter;

    private long startEpochDay;
    private long endEpochDay;
    private boolean updatingRange;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rangeGroup = view.findViewById(R.id.range_group);
        rangeView = view.findViewById(R.id.tv_range);
        totalView = view.findViewById(R.id.tv_total);
        dailyAvgView = view.findViewById(R.id.tv_daily_avg);
        appsView = view.findViewById(R.id.tv_apps);
        activeDaysView = view.findViewById(R.id.tv_active_days);
        peakView = view.findViewById(R.id.tv_peak);
        filterNote = view.findViewById(R.id.tv_filter_note);
        emptyView = view.findViewById(R.id.tv_empty);
        chart = view.findViewById(R.id.chart);
        recycler = view.findViewById(R.id.rv_apps);

        adapter = new AppStatAdapter(new AppIconLoader(requireContext()),
                usage -> AppDetailActivity.start(requireContext(), usage.packageName, endEpochDay),
                R.string.usage_percent_range);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        rangeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || updatingRange) {
                return;
            }
            int days = checkedId == R.id.btn_30 ? 30 : checkedId == R.id.btn_14 ? 14 : 7;
            applyPreset(days);
        });
        view.findViewById(R.id.btn_custom).setOnClickListener(v -> openRangePicker());

        updatingRange = true;
        rangeGroup.check(R.id.btn_7);
        updatingRange = false;
        applyPreset(7);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh(true);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void applyPreset(int days) {
        LocalDate today = LocalDate.now();
        startEpochDay = today.minusDays(days - 1L).toEpochDay();
        endEpochDay = today.toEpochDay();
        updateRangeLabel();
        refresh(true);
    }

    private void updateRangeLabel() {
        LocalDate start = LocalDate.ofEpochDay(startEpochDay);
        LocalDate end = LocalDate.ofEpochDay(endEpochDay);
        rangeView.setText(getString(R.string.stats_range_label,
                RANGE_DATE.format(start), RANGE_DATE.format(end)));
    }

    private void openRangePicker() {
        long todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long startLimit = LocalDate.now().minusDays(MAX_RANGE_DAYS - 1L)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(startLimit)
                .setEnd(todayUtc)
                .build();
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker =
                MaterialDatePicker.Builder.dateRangePicker()
                        .setTitleText(R.string.stats_range)
                        .setCalendarConstraints(constraints)
                        .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) {
                return;
            }
            LocalDate start = Instant.ofEpochMilli(selection.first)
                    .atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate end = Instant.ofEpochMilli(selection.second)
                    .atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate today = LocalDate.now();
            if (end.isAfter(today)) {
                end = today;
            }
            if (start.isAfter(end)) {
                start = end;
            }
            if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
                start = end.minusDays(MAX_RANGE_DAYS - 1L);
                Toast.makeText(requireContext(), R.string.stats_range_limited,
                        Toast.LENGTH_SHORT).show();
            }
            startEpochDay = start.toEpochDay();
            endEpochDay = end.toEpochDay();
            updatingRange = true;
            rangeGroup.clearChecked();
            updatingRange = false;
            updateRangeLabel();
            refresh(true);
        });
        picker.show(getParentFragmentManager(), "stats-range");
    }

    private void refresh(boolean sync) {
        if (!isAdded() || getView() == null) {
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        final long start = startEpochDay;
        final long end = endEpochDay;
        final int rangeDays = (int) (end - start + 1);
        executor.execute(() -> {
            if (sync && AppUtils.isUsageAccessGranted(appContext)) {
                UsageSync.syncRecentDays(appContext, 14);
            }
            Map<String, Long> appTotals = new HashMap<>();
            Map<String, String> labels = new HashMap<>();
            List<DailyTotal> daily = new ArrayList<>();
            long total = 0L;
            int activeDays = 0;

            UsageStore store = new UsageStore(appContext);
            try {
                LocalDate day = LocalDate.ofEpochDay(start);
                LocalDate last = LocalDate.ofEpochDay(end);
                while (!day.isAfter(last)) {
                    long dayTotal = 0L;
                    for (AppUsage usage : store.getDay(day.toEpochDay())) {
                        dayTotal += usage.durationMs;
                        Long previous = appTotals.get(usage.packageName);
                        appTotals.put(usage.packageName,
                                previous == null ? usage.durationMs : previous + usage.durationMs);
                        labels.put(usage.packageName, usage.appLabel);
                    }
                    daily.add(new DailyTotal(day.toEpochDay(), dayTotal));
                    total += dayTotal;
                    if (dayTotal > 0) {
                        activeDays++;
                    }
                    day = day.plusDays(1);
                }
            } finally {
                store.close();
            }

            long minDuration = DisplayPrefs.getMinDuration(appContext);
            List<AppUsage> apps = new ArrayList<>();
            for (Map.Entry<String, Long> entry : appTotals.entrySet()) {
                if (entry.getValue() >= minDuration) {
                    apps.add(new AppUsage(entry.getKey(),
                            labels.get(entry.getKey()), entry.getValue()));
                }
            }
            apps.sort((a, b) -> Long.compare(b.durationMs, a.durationMs));

            long peak = 0L;
            for (DailyTotal item : daily) {
                peak = Math.max(peak, item.durationMs);
            }

            final long totalDuration = total;
            final int active = activeDays;
            final long average = total / Math.max(1, rangeDays);
            final long peakValue = peak;
            final List<AppUsage> appList = apps;
            final List<DailyTotal> dailyList = daily;
            final long min = minDuration;

            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                totalView.setText(TimeUtils.formatDuration(totalDuration));
                dailyAvgView.setText(TimeUtils.formatDuration(average));
                appsView.setText(String.valueOf(appList.size()));
                activeDaysView.setText(getString(R.string.stats_active_days_value,
                        active, rangeDays));
                peakView.setText(getString(R.string.stats_peak,
                        TimeUtils.formatCompact(peakValue)));
                chart.setData(dailyList);
                adapter.submit(appList, totalDuration);
                if (min > 0) {
                    filterNote.setText(getString(R.string.filter_note,
                            DisplayPrefs.label(min)));
                    filterNote.setVisibility(View.VISIBLE);
                } else {
                    filterNote.setVisibility(View.GONE);
                }
                boolean empty = appList.isEmpty();
                if (empty && min > 0 && totalDuration > 0) {
                    emptyView.setText(R.string.stats_empty_filtered);
                } else {
                    emptyView.setText(R.string.stats_empty);
                }
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }
}

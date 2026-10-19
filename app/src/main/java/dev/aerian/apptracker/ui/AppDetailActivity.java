package dev.aerian.apptracker.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppLabelResolver;
import dev.aerian.apptracker.data.BlacklistStore;
import dev.aerian.apptracker.data.DisplayPrefs;
import dev.aerian.apptracker.data.DayUsage;
import dev.aerian.apptracker.data.UsageInterval;
import dev.aerian.apptracker.data.UsageStore;
import dev.aerian.apptracker.data.UsageSync;
import dev.aerian.apptracker.ui.adapter.IntervalAdapter;
import dev.aerian.apptracker.util.AppIconLoader;
import dev.aerian.apptracker.util.AppUtils;
import dev.aerian.apptracker.util.DayUtils;
import dev.aerian.apptracker.util.TimeUtils;
import dev.aerian.apptracker.util.WindowInsetsHelper;

public class AppDetailActivity extends AppCompatActivity {

    private static final String EXTRA_PACKAGE = "package_name";
    private static final String EXTRA_EPOCH_DAY = "epoch_day";
    private static final int SYNC_DAYS = 14;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private IntervalAdapter adapter;
    private TextView totalView;
    private TextView last7View;
    private TextView averageView;
    private TextView dateView;
    private TextView emptyView;
    private RecyclerView recycler;
    private ImageButton nextButton;
    private Chip timeChip;

    private String packageName;
    private LocalDate selectedDate;
    private int timeStartMinute = -1;
    private int timeEndMinute = -1;

    public static void start(Context context, String packageName, long epochDay) {
        Intent intent = new Intent(context, AppDetailActivity.class);
        intent.putExtra(EXTRA_PACKAGE, packageName);
        intent.putExtra(EXTRA_EPOCH_DAY, epochDay);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_detail);
        WindowInsetsHelper.apply(findViewById(R.id.root));

        packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
        long epochDay = getIntent().getLongExtra(EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay());
        selectedDate = LocalDate.ofEpochDay(epochDay);
        if (packageName == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        String label = AppLabelResolver.label(this, packageName);
        toolbar.setTitle(label);
        toolbar.inflateMenu(R.menu.menu_app_detail);
        MenuItem blacklistItem = toolbar.getMenu().findItem(R.id.action_blacklist);
        updateBlacklistMenuItem(blacklistItem);
        blacklistItem.setOnMenuItemClickListener(item -> {
            boolean blacklisted = !BlacklistStore.isBlacklisted(this, packageName);
            BlacklistStore.setBlacklisted(this, packageName, blacklisted);
            Toast.makeText(this,
                    blacklisted ? R.string.blacklist_added : R.string.blacklist_removed,
                    Toast.LENGTH_SHORT).show();
            updateBlacklistMenuItem(item);
            loadData();
            return true;
        });

        ImageView iconView = findViewById(R.id.iv_icon);
        iconView.setImageDrawable(new AppIconLoader(this).load(packageName));
        ((TextView) findViewById(R.id.tv_name)).setText(label);
        ((TextView) findViewById(R.id.tv_package)).setText(packageName);

        totalView = findViewById(R.id.tv_total);
        last7View = findViewById(R.id.tv_launches);
        averageView = findViewById(R.id.tv_longest);
        dateView = findViewById(R.id.tv_date);
        emptyView = findViewById(R.id.tv_empty);
        recycler = findViewById(R.id.rv_sessions);
        nextButton = findViewById(R.id.btn_next);
        timeChip = findViewById(R.id.chip_time_filter);
        timeStartMinute = DisplayPrefs.getAppDetailStartMinute(this);
        timeEndMinute = DisplayPrefs.getAppDetailEndMinute(this);
        updateTimeChip();
        timeChip.setOnClickListener(v -> showTimeFilterDialog());
        timeChip.setOnCloseIconClickListener(v -> {
            DisplayPrefs.clearAppDetailTimeRange(this);
            timeStartMinute = -1;
            timeEndMinute = -1;
            updateTimeChip();
            loadData();
        });

        adapter = new IntervalAdapter(new AppIconLoader(this), null);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_prev).setOnClickListener(v -> {
            selectedDate = selectedDate.minusDays(1);
            updateDateHeader();
            loadData();
        });
        nextButton.setOnClickListener(v -> {
            if (selectedDate.isBefore(LocalDate.now())) {
                selectedDate = selectedDate.plusDays(1);
                updateDateHeader();
                loadData();
            }
        });
        dateView.setOnClickListener(v -> showDatePicker());
        updateDateHeader();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void updateBlacklistMenuItem(MenuItem item) {
        item.setTitle(BlacklistStore.isBlacklisted(this, packageName)
                ? R.string.blacklist_remove : R.string.blacklist_add);
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this,
                (picker, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    updateDateHeader();
                    loadData();
                },
                selectedDate.getYear(), selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(DayUtils.dayEnd(LocalDate.now()) - 1L);
        dialog.show();
    }

    private void updateDateHeader() {
        LocalDate today = LocalDate.now();
        String text;
        if (selectedDate.getYear() != today.getYear()) {
            text = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
                    .format(selectedDate);
        } else {
            text = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA).format(selectedDate);
        }
        if (selectedDate.equals(today)) {
            text = getString(R.string.history_today) + " · " + text;
        }
        dateView.setText(text);
        boolean canGoForward = selectedDate.isBefore(today);
        nextButton.setEnabled(canGoForward);
        nextButton.setAlpha(canGoForward ? 1f : 0.3f);
    }

    private void loadData() {
        final Context appContext = getApplicationContext();
        final LocalDate today = LocalDate.now();
        final long selectedDay = selectedDate.toEpochDay();
        final long last7Start = today.minusDays(6).toEpochDay();
        final int filterStart = timeStartMinute;
        final int filterEnd = timeEndMinute;

        executor.execute(() -> {
            if (AppUtils.isUsageAccessGranted(appContext)) {
                UsageSync.syncRecentDays(appContext, SYNC_DAYS);
            }
            UsageStore store = new UsageStore(appContext);
            List<UsageInterval> intervals;
            long last7Total = 0L;
            try {
                intervals = store.getIntervalsForPackage(selectedDay, packageName);
                List<DayUsage> history =
                        store.getAppHistory(packageName, last7Start, today.toEpochDay());
                for (DayUsage item : history) {
                    last7Total += item.durationMs;
                }
            } finally {
                store.close();
            }
            boolean timeFiltered = filterStart >= 0 && filterEnd >= 0;
            intervals = applyTimeFilter(intervals, selectedDay, filterStart, filterEnd);
            long selectedTotal = 0L;
            for (UsageInterval item : intervals) {
                selectedTotal += item.durationMs();
            }
            long minDuration = DisplayPrefs.getMinDuration(appContext);
            if (minDuration > 0) {
                intervals.removeIf(item -> item.durationMs() < minDuration);
            }

            final boolean filteredByTime = timeFiltered;
            final long selectedValue = selectedTotal;
            final long last7Value = last7Total;
            final long averageValue = last7Value / 7L;
            final List<UsageInterval> rows = intervals;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                totalView.setText(TimeUtils.formatDuration(selectedValue));
                last7View.setText(TimeUtils.formatDuration(last7Value));
                averageView.setText(TimeUtils.formatDuration(averageValue));
                adapter.submit(rows);
                boolean empty = rows.isEmpty();
                if (empty && filteredByTime) {
                    emptyView.setText(R.string.app_detail_time_empty);
                } else if (empty && selectedValue > 0) {
                    emptyView.setText(R.string.app_detail_day_empty_filtered);
                } else {
                    emptyView.setText(R.string.app_detail_day_empty);
                }
                emptyView.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
                recycler.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
            });
        });
    }

    private List<UsageInterval> applyTimeFilter(List<UsageInterval> intervals,
                                                long epochDay, int startMinute, int endMinute) {
        if (startMinute < 0 || endMinute < 0) {
            return intervals;
        }
        long dayStart = DayUtils.dayStart(LocalDate.ofEpochDay(epochDay));
        long dayEnd = DayUtils.dayEnd(LocalDate.ofEpochDay(epochDay));
        long rangeStart = Math.min(dayEnd, dayStart + startMinute * 60_000L);
        long rangeEnd = Math.min(dayEnd, dayStart + (endMinute + 1L) * 60_000L);
        List<UsageInterval> filtered = new ArrayList<>();
        for (UsageInterval item : intervals) {
            long start = Math.max(item.startTime, rangeStart);
            long end = Math.min(item.endTime, rangeEnd);
            if (end - start >= 1_000L) {
                filtered.add(new UsageInterval(item.packageName, item.appLabel,
                        start, end, false));
            }
        }
        return filtered;
    }

    private void updateTimeChip() {
        boolean active = timeStartMinute >= 0 && timeEndMinute >= 0;
        if (active) {
            timeChip.setText(getString(R.string.time_filter_range,
                    formatMinute(timeStartMinute), formatMinute(timeEndMinute)));
        } else {
            timeChip.setText(R.string.time_filter_all);
        }
        timeChip.setCloseIconVisible(active);
    }

    private void showTimeFilterDialog() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_time_filter, null);
        MaterialButton startButton = content.findViewById(R.id.btn_start_time);
        MaterialButton endButton = content.findViewById(R.id.btn_end_time);

        final int[] start = {timeStartMinute >= 0 ? timeStartMinute : 0};
        final int[] end = {timeEndMinute >= 0 ? timeEndMinute : 23 * 60 + 59};
        startButton.setText(formatMinute(start[0]));
        endButton.setText(formatMinute(end[0]));

        startButton.setOnClickListener(v -> new TimePickerDialog(this,
                (view, hour, minute) -> {
                    start[0] = hour * 60 + minute;
                    startButton.setText(formatMinute(start[0]));
                }, start[0] / 60, start[0] % 60, true).show());
        endButton.setOnClickListener(v -> new TimePickerDialog(this,
                (view, hour, minute) -> {
                    end[0] = hour * 60 + minute;
                    endButton.setText(formatMinute(end[0]));
                }, end[0] / 60, end[0] % 60, true).show());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.time_filter_title)
                .setView(content)
                .setPositiveButton(R.string.time_filter_apply, null)
                .setNegativeButton(R.string.settings_cancel, null)
                .setNeutralButton(R.string.time_filter_clear, (d, which) -> {
                    DisplayPrefs.clearAppDetailTimeRange(this);
                    timeStartMinute = -1;
                    timeEndMinute = -1;
                    updateTimeChip();
                    loadData();
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (end[0] <= start[0]) {
                        Toast.makeText(this, R.string.time_filter_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DisplayPrefs.setAppDetailTimeRange(this, start[0], end[0]);
                    timeStartMinute = start[0];
                    timeEndMinute = end[0];
                    updateTimeChip();
                    loadData();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static String formatMinute(int minute) {
        return String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60);
    }
}

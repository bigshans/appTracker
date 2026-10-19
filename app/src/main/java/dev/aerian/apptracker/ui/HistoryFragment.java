package dev.aerian.apptracker.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppUsage;
import dev.aerian.apptracker.data.DisplayPrefs;
import dev.aerian.apptracker.data.UsageInterval;
import dev.aerian.apptracker.data.UsageStore;
import dev.aerian.apptracker.data.UsageSync;
import dev.aerian.apptracker.ui.adapter.IntervalAdapter;
import dev.aerian.apptracker.util.AppIconLoader;
import dev.aerian.apptracker.util.AppUtils;
import dev.aerian.apptracker.util.DayUtils;
import dev.aerian.apptracker.util.TimeUtils;

public class HistoryFragment extends Fragment {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private IntervalAdapter adapter;
    private TextView dateView;
    private TextView summaryView;
    private TextView emptyView;
    private RecyclerView recycler;
    private ImageButton nextButton;
    private Chip timeChip;
    private EditText searchView;

    private LocalDate selectedDate = LocalDate.now();
    private int timeStartMinute = -1;
    private int timeEndMinute = -1;
    private List<UsageInterval> baseIntervals = new ArrayList<>();
    private long baseTotal;
    private boolean currentTimeFiltered;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dateView = view.findViewById(R.id.tv_date);
        summaryView = view.findViewById(R.id.tv_summary);
        emptyView = view.findViewById(R.id.tv_empty);
        recycler = view.findViewById(R.id.rv_sessions);
        nextButton = view.findViewById(R.id.btn_next);
        timeChip = view.findViewById(R.id.chip_time_filter);
        searchView = view.findViewById(R.id.et_search_apps);
        timeStartMinute = DisplayPrefs.getHistoryStartMinute(requireContext());
        timeEndMinute = DisplayPrefs.getHistoryEndMinute(requireContext());
        updateTimeChip();

        timeChip.setOnClickListener(v -> showTimeFilterDialog());
        timeChip.setOnCloseIconClickListener(v -> {
            DisplayPrefs.clearHistoryTimeRange(requireContext());
            timeStartMinute = -1;
            timeEndMinute = -1;
            updateTimeChip();
            loadData(false);
        });
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        searchView.clearFocus();

        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyDisplayFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed.
            }
        });

        adapter = new IntervalAdapter(new AppIconLoader(requireContext()), interval ->
                AppDetailActivity.start(requireContext(), interval.packageName,
                        selectedDate.toEpochDay()));
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btn_prev).setOnClickListener(v -> {
            selectedDate = selectedDate.minusDays(1);
            updateHeader();
            loadData(false);
        });
        nextButton.setOnClickListener(v -> {
            if (selectedDate.isBefore(LocalDate.now())) {
                selectedDate = selectedDate.plusDays(1);
                updateHeader();
                loadData(false);
            }
        });
        dateView.setOnClickListener(v -> showDatePicker());
        updateHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateHeader();
        loadData(true);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(requireContext(),
                (picker, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    updateHeader();
                    loadData(false);
                },
                selectedDate.getYear(), selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth());
        dialog.getDatePicker().setMaxDate(DayUtils.dayEnd(LocalDate.now()) - 1L);
        dialog.show();
    }

    private void updateHeader() {
        if (!isAdded() || getView() == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        String text = DATE_FORMAT.format(selectedDate);
        if (selectedDate.equals(today)) {
            text = getString(R.string.history_today) + " · " + text;
        } else if (selectedDate.getYear() != today.getYear()) {
            text = DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)
                    .format(selectedDate);
        }
        dateView.setText(text);
        boolean canGoForward = selectedDate.isBefore(today);
        nextButton.setEnabled(canGoForward);
        nextButton.setAlpha(canGoForward ? 1f : 0.3f);
    }

    private void loadData(boolean sync) {
        if (!isAdded() || getView() == null) {
            return;
        }
        final LocalDate date = selectedDate;
        final long epochDay = date.toEpochDay();
        final int filterStart = timeStartMinute;
        final int filterEnd = timeEndMinute;
        final Context appContext = requireContext().getApplicationContext();

        executor.execute(() -> {
            if (sync && AppUtils.isUsageAccessGranted(appContext)) {
                UsageSync.syncRecentDays(appContext, 14);
            }
            UsageStore store = new UsageStore(appContext);
            List<AppUsage> usage;
            List<UsageInterval> intervals;
            try {
                usage = store.getDay(epochDay);
                intervals = store.getIntervals(epochDay);
            } finally {
                store.close();
            }
            long fullTotal = 0L;
            for (AppUsage item : usage) {
                fullTotal += item.durationMs;
            }
            final boolean timeFiltered = filterStart >= 0 && filterEnd >= 0;
            intervals = applyTimeFilter(intervals, epochDay, filterStart, filterEnd);
            long queryTotal = 0L;
            for (UsageInterval item : intervals) {
                queryTotal += item.durationMs();
            }
            final long totalDuration = timeFiltered ? queryTotal : fullTotal;
            final List<UsageInterval> rows = intervals;
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                baseIntervals = rows;
                baseTotal = totalDuration;
                currentTimeFiltered = timeFiltered;
                applyDisplayFilters();
            });
        });
    }

    private void applyDisplayFilters() {
        if (!isAdded() || getView() == null) {
            return;
        }
        long minDuration = DisplayPrefs.getMinDuration(requireContext());
        String query = searchView == null ? ""
                : searchView.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<UsageInterval> visible = new ArrayList<>();
        Set<String> visiblePackages = new HashSet<>();
        for (UsageInterval item : baseIntervals) {
            if (item.durationMs() < minDuration) {
                continue;
            }
            if (!query.isEmpty()
                    && !item.appLabel.toLowerCase(Locale.ROOT).contains(query)
                    && !item.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            visible.add(item);
            visiblePackages.add(item.packageName);
        }
        adapter.submit(visible);
        if (!visible.isEmpty()) {
            recycler.scrollToPosition(0);
        }
        String summary = getString(R.string.history_summary,
                TimeUtils.formatDuration(baseTotal), visiblePackages.size());
        if (currentTimeFiltered) {
            summary += " · " + getString(R.string.time_filter_range,
                    formatMinute(timeStartMinute), formatMinute(timeEndMinute));
        }
        if (minDuration > 0) {
            summary += " · " + getString(R.string.filter_note,
                    DisplayPrefs.label(minDuration));
        }
        summaryView.setText(summary);
        boolean empty = visible.isEmpty();
        if (empty && !query.isEmpty()) {
            emptyView.setText(R.string.history_empty_search);
        } else if (empty && currentTimeFiltered) {
            emptyView.setText(R.string.history_empty_time);
        } else if (empty && minDuration > 0 && baseTotal > 0) {
            emptyView.setText(R.string.history_empty_filtered);
        } else {
            emptyView.setText(R.string.history_empty);
        }
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
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
        if (!isAdded()) {
            return;
        }
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_time_filter, null);
        com.google.android.material.button.MaterialButton startButton =
                content.findViewById(R.id.btn_start_time);
        com.google.android.material.button.MaterialButton endButton =
                content.findViewById(R.id.btn_end_time);

        final int[] start = {timeStartMinute >= 0 ? timeStartMinute : 0};
        final int[] end = {timeEndMinute >= 0 ? timeEndMinute : 23 * 60 + 59};
        startButton.setText(formatMinute(start[0]));
        endButton.setText(formatMinute(end[0]));

        startButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(requireContext(),
                    (view, hour, minute) -> {
                        start[0] = hour * 60 + minute;
                        startButton.setText(formatMinute(start[0]));
                    }, start[0] / 60, start[0] % 60, true);
            picker.show();
        });
        endButton.setOnClickListener(v -> {
            TimePickerDialog picker = new TimePickerDialog(requireContext(),
                    (view, hour, minute) -> {
                        end[0] = hour * 60 + minute;
                        endButton.setText(formatMinute(end[0]));
                    }, end[0] / 60, end[0] % 60, true);
            picker.show();
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.time_filter_title)
                .setView(content)
                .setPositiveButton(R.string.time_filter_apply, null)
                .setNegativeButton(R.string.settings_cancel, null)
                .setNeutralButton(R.string.time_filter_clear, (d, which) -> {
                    DisplayPrefs.clearHistoryTimeRange(requireContext());
                    timeStartMinute = -1;
                    timeEndMinute = -1;
                    updateTimeChip();
                    loadData(false);
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (end[0] <= start[0]) {
                        Toast.makeText(requireContext(), R.string.time_filter_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DisplayPrefs.setHistoryTimeRange(requireContext(), start[0], end[0]);
                    timeStartMinute = start[0];
                    timeEndMinute = end[0];
                    updateTimeChip();
                    loadData(false);
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static String formatMinute(int minute) {
        return String.format(Locale.CHINA, "%02d:%02d", minute / 60, minute % 60);
    }
}

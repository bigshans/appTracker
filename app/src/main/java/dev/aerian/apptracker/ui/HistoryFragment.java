package dev.aerian.apptracker.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private LocalDate selectedDate = LocalDate.now();

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
            long total = 0L;
            for (AppUsage item : usage) {
                total += item.durationMs;
            }
            final long totalDuration = total;
            final long minDuration = DisplayPrefs.getMinDuration(appContext);
            if (minDuration > 0) {
                intervals.removeIf(item -> item.durationMs() < minDuration);
            }
            Set<String> visiblePackages = new HashSet<>();
            for (UsageInterval item : intervals) {
                visiblePackages.add(item.packageName);
            }
            final int visibleApps = visiblePackages.size();
            final List<UsageInterval> rows = intervals;
            mainHandler.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                adapter.submit(rows);
                String summary = getString(R.string.history_summary,
                        TimeUtils.formatDuration(totalDuration), visibleApps);
                if (minDuration > 0) {
                    summary += " · " + getString(R.string.filter_note,
                            DisplayPrefs.label(minDuration));
                }
                summaryView.setText(summary);
                boolean empty = rows.isEmpty();
                if (empty && minDuration > 0 && totalDuration > 0) {
                    emptyView.setText(R.string.history_empty_filtered);
                } else {
                    emptyView.setText(R.string.history_empty);
                }
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        });
    }
}

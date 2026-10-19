package dev.aerian.apptracker.ui;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppLabelResolver;
import dev.aerian.apptracker.data.BlacklistStore;
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

    private String packageName;
    private LocalDate selectedDate;

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
            long selectedTotal = 0L;
            for (UsageInterval item : intervals) {
                selectedTotal += item.durationMs();
            }

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
                emptyView.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
                recycler.setVisibility(empty ? android.view.View.GONE : android.view.View.VISIBLE);
            });
        });
    }
}

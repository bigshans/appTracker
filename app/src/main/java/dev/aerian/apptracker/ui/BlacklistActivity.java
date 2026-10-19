package dev.aerian.apptracker.ui;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppLabelResolver;
import dev.aerian.apptracker.data.BlacklistStore;
import dev.aerian.apptracker.ui.adapter.BlacklistAdapter;
import dev.aerian.apptracker.util.WindowInsetsHelper;

public class BlacklistActivity extends AppCompatActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BlacklistAdapter adapter;
    private List<BlacklistAdapter.AppEntry> userEntries = new ArrayList<>();
    private List<BlacklistAdapter.AppEntry> allEntries = new ArrayList<>();
    private TextView summaryView;
    private TextView emptyView;
    private RecyclerView recycler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blacklist);
        WindowInsetsHelper.apply(findViewById(R.id.root));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        summaryView = findViewById(R.id.tv_summary);
        emptyView = findViewById(R.id.tv_empty);
        recycler = findViewById(R.id.rv_apps);

        adapter = new BlacklistAdapter(this, count -> {
            updateSummary(count, adapter.getTotalCount());
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        MaterialSwitch showAll = findViewById(R.id.switch_show_all);
        showAll.setOnCheckedChangeListener((button, checked) -> {
            adapter.submit(checked ? allEntries : userEntries);
            updateSummary(BlacklistStore.count(this), adapter.getTotalCount());
            updateEmptyState();
        });

        TextInputEditText search = findViewById(R.id.et_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s == null ? "" : s.toString());
                updateEmptyState();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed.
            }
        });

        loadApps();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadApps() {
        executor.execute(() -> {
            Set<String> launcherPackages = new HashSet<>();
            Set<String> installedPackages = new HashSet<>();
            try {
                Intent launcher = new Intent(Intent.ACTION_MAIN);
                launcher.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> resolved = getPackageManager().queryIntentActivities(launcher, 0);
                for (ResolveInfo info : resolved) {
                    if (info.activityInfo != null) {
                        launcherPackages.add(info.activityInfo.packageName);
                    }
                }
                for (ApplicationInfo info : getPackageManager().getInstalledApplications(0)) {
                    installedPackages.add(info.packageName);
                }
            } catch (Exception ignored) {
                // Fall through with blacklisted packages only.
            }

            Set<String> blacklisted = BlacklistStore.getAll(this);
            installedPackages.addAll(blacklisted);
            launcherPackages.addAll(blacklisted);
            installedPackages.remove(getPackageName());
            launcherPackages.remove(getPackageName());

            List<BlacklistAdapter.AppEntry> visible = new ArrayList<>();
            List<BlacklistAdapter.AppEntry> all = new ArrayList<>();
            for (String pkg : installedPackages) {
                BlacklistAdapter.AppEntry entry = new BlacklistAdapter.AppEntry(
                        pkg, AppLabelResolver.label(this, pkg));
                all.add(entry);
                if (launcherPackages.contains(pkg)) {
                    visible.add(entry);
                }
            }
            Collator collator = Collator.getInstance(Locale.CHINA);
            visible.sort((a, b) -> collator.compare(a.label, b.label));
            all.sort((a, b) -> collator.compare(a.label, b.label));

            final List<BlacklistAdapter.AppEntry> visibleResult = visible;
            final List<BlacklistAdapter.AppEntry> allResult = all;
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                userEntries = visibleResult;
                allEntries = allResult;
                adapter.submit(visibleResult);
                updateSummary(BlacklistStore.count(this), visibleResult.size());
                updateEmptyState();
            });
        });
    }

    private void updateSummary(int blacklisted, int total) {
        summaryView.setText(getString(R.string.blacklist_selected, blacklisted, total));
    }

    private void updateEmptyState() {
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}

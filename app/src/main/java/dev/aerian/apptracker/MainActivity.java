package dev.aerian.apptracker;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import dev.aerian.apptracker.data.SyncScheduler;
import dev.aerian.apptracker.ui.HistoryFragment;
import dev.aerian.apptracker.ui.OverviewFragment;
import dev.aerian.apptracker.ui.SettingsFragment;
import dev.aerian.apptracker.util.WindowInsetsHelper;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_TAB = "selected_tab";

    private int selectedTab = R.id.nav_overview;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WindowInsetsHelper.apply(findViewById(R.id.root));

        if (savedInstanceState != null) {
            selectedTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_overview);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            switchTo(item.getItemId());
            return true;
        });

        if (getSupportFragmentManager().findFragmentById(R.id.fragment_container) == null) {
            switchTo(selectedTab);
        }
        bottomNav.setSelectedItemId(selectedTab);

        SyncScheduler.schedulePeriodic(this);
    }

    public void selectTab(int itemId) {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(itemId);
    }

    private void switchTo(int itemId) {
        Fragment existing = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (itemId == selectedTab && existing != null) {
            return;
        }
        selectedTab = itemId;

        Fragment fragment;
        if (itemId == R.id.nav_history) {
            fragment = new HistoryFragment();
        } else if (itemId == R.id.nav_settings) {
            fragment = new SettingsFragment();
        } else {
            fragment = new OverviewFragment();
        }

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, selectedTab);
    }
}

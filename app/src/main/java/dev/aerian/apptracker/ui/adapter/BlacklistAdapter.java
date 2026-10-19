package dev.aerian.apptracker.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.BlacklistStore;
import dev.aerian.apptracker.util.AppIconLoader;

public class BlacklistAdapter extends RecyclerView.Adapter<BlacklistAdapter.Holder> {

    public interface Listener {
        void onBlacklistCountChanged(int count);
    }

    public static final class AppEntry {
        public final String packageName;
        public final String label;

        public AppEntry(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private final AppIconLoader iconLoader;
    private final Listener listener;
    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private String query = "";

    public BlacklistAdapter(Context context, Listener listener) {
        this.iconLoader = new AppIconLoader(context);
        this.listener = listener;
    }

    public void submit(List<AppEntry> entries) {
        all.clear();
        all.addAll(entries);
        applyFilter();
    }

    public void setQuery(String value) {
        query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        applyFilter();
    }

    private void applyFilter() {
        shown.clear();
        if (query.isEmpty()) {
            shown.addAll(all);
        } else {
            for (AppEntry entry : all) {
                if (entry.label.toLowerCase(Locale.ROOT).contains(query)
                        || entry.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                    shown.add(entry);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_blacklist_app, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppEntry entry = shown.get(position);
        Context context = holder.itemView.getContext();
        holder.icon.setImageDrawable(iconLoader.load(entry.packageName));
        holder.name.setText(entry.label);
        holder.packageName.setText(entry.packageName);

        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(BlacklistStore.isBlacklisted(context, entry.packageName));
        holder.toggle.setOnCheckedChangeListener((button, checked) -> {
            BlacklistStore.setBlacklisted(context, entry.packageName, checked);
            if (listener != null) {
                listener.onBlacklistCountChanged(BlacklistStore.count(context));
            }
        });
        holder.itemView.setOnClickListener(v -> holder.toggle.toggle());
    }

    public int getTotalCount() {
        return all.size();
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView packageName;
        final MaterialSwitch toggle;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
            name = itemView.findViewById(R.id.tv_name);
            packageName = itemView.findViewById(R.id.tv_package);
            toggle = itemView.findViewById(R.id.switch_blacklist);
        }
    }
}

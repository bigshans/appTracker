package dev.aerian.apptracker.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.AppUsage;
import dev.aerian.apptracker.util.AppIconLoader;
import dev.aerian.apptracker.util.TimeUtils;

public class AppStatAdapter extends RecyclerView.Adapter<AppStatAdapter.Holder> {

    public interface Listener {
        void onAppClicked(AppUsage usage);
    }

    private final AppIconLoader iconLoader;
    private final Listener listener;
    private final int percentFormatRes;
    private final List<AppUsage> items = new ArrayList<>();
    private long maxDuration = 1L;
    private long totalDuration = 1L;

    public AppStatAdapter(AppIconLoader iconLoader, Listener listener) {
        this(iconLoader, listener, R.string.usage_percent);
    }

    public AppStatAdapter(AppIconLoader iconLoader, Listener listener, int percentFormatRes) {
        this.iconLoader = iconLoader;
        this.listener = listener;
        this.percentFormatRes = percentFormatRes;
    }

    public void submit(List<AppUsage> usage, long total) {
        items.clear();
        items.addAll(usage);
        totalDuration = Math.max(1L, total);
        maxDuration = totalDuration;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_stat, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppUsage usage = items.get(position);
        holder.icon.setImageDrawable(iconLoader.load(usage.packageName));
        holder.name.setText(usage.appLabel);
        holder.duration.setText(TimeUtils.formatCompact(usage.durationMs));
        int percent = (int) Math.min(100L, Math.round(usage.durationMs * 100.0 / totalDuration));
        holder.subtitle.setText(holder.itemView.getContext().getString(
                percentFormatRes, percent));
        int progress = (int) Math.min(1000L, usage.durationMs * 1000L / maxDuration);
        holder.progress.setProgressCompat(progress, false);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAppClicked(usage);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;
        final TextView duration;
        final TextView subtitle;
        final LinearProgressIndicator progress;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
            name = itemView.findViewById(R.id.tv_name);
            duration = itemView.findViewById(R.id.tv_duration);
            subtitle = itemView.findViewById(R.id.tv_subtitle);
            progress = itemView.findViewById(R.id.progress);
        }
    }
}

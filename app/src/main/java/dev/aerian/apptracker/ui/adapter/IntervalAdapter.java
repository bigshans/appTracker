package dev.aerian.apptracker.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import dev.aerian.apptracker.R;
import dev.aerian.apptracker.data.UsageInterval;
import dev.aerian.apptracker.util.AppIconLoader;
import dev.aerian.apptracker.util.TimeUtils;

/** Chronological foreground-app timetable. */
public class IntervalAdapter extends RecyclerView.Adapter<IntervalAdapter.Holder> {

    public interface Listener {
        void onIntervalClicked(UsageInterval interval);
    }

    private final AppIconLoader iconLoader;
    private final Listener listener;
    private final List<UsageInterval> items = new ArrayList<>();

    public IntervalAdapter(AppIconLoader iconLoader, Listener listener) {
        this.iconLoader = iconLoader;
        this.listener = listener;
    }

    public void submit(List<UsageInterval> intervals) {
        items.clear();
        items.addAll(intervals);
        items.sort((a, b) -> Long.compare(b.startTime, a.startTime));
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        UsageInterval item = items.get(position);
        holder.icon.setImageDrawable(iconLoader.load(item.packageName));
        holder.name.setText(item.appLabel);
        String range;
        if (item.ongoing) {
            range = TimeUtils.formatTimeOfDaySeconds(item.startTime)
                    + " – " + holder.itemView.getContext().getString(R.string.history_live);
        } else {
            range = TimeUtils.formatTimeOfDaySeconds(item.startTime)
                    + " – " + TimeUtils.formatTimeOfDaySeconds(item.endTime);
        }
        holder.range.setText(range);
        holder.duration.setText(TimeUtils.formatDuration(item.durationMs()));
        holder.duration.setTextColor(item.ongoing
                ? ContextCompat.getColor(holder.itemView.getContext(), R.color.primary)
                : holder.range.getCurrentTextColor());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onIntervalClicked(item);
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
        final TextView range;
        final TextView duration;

        Holder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
            name = itemView.findViewById(R.id.tv_name);
            range = itemView.findViewById(R.id.tv_range);
            duration = itemView.findViewById(R.id.tv_duration);
        }
    }
}

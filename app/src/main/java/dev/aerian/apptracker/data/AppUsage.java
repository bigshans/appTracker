package dev.aerian.apptracker.data;

/** Aggregated foreground duration for one application on one day. */
public final class AppUsage {

    public final String packageName;
    public final String appLabel;
    public final long durationMs;

    public AppUsage(String packageName, String appLabel, long durationMs) {
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.durationMs = durationMs;
    }
}

package dev.aerian.apptracker.data;

/** One continuous foreground interval for one application. */
public final class UsageInterval {

    public final String packageName;
    public final String appLabel;
    public final long startTime;
    public final long endTime;
    public final boolean ongoing;

    public UsageInterval(String packageName, String appLabel,
                         long startTime, long endTime, boolean ongoing) {
        this.packageName = packageName;
        this.appLabel = appLabel;
        this.startTime = startTime;
        this.endTime = endTime;
        this.ongoing = ongoing;
    }

    public long durationMs() {
        return Math.max(0L, endTime - startTime);
    }
}

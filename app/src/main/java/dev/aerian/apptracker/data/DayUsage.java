package dev.aerian.apptracker.data;

/** Aggregated duration for one application on one epoch day. */
public final class DayUsage {

    public final long epochDay;
    public final long durationMs;

    public DayUsage(long epochDay, long durationMs) {
        this.epochDay = epochDay;
        this.durationMs = durationMs;
    }
}

package dev.aerian.apptracker.data;

/** Total foreground duration for one day, used by the statistics chart. */
public final class DailyTotal {

    public final long epochDay;
    public final long durationMs;

    public DailyTotal(long epochDay, long durationMs) {
        this.epochDay = epochDay;
        this.durationMs = durationMs;
    }
}

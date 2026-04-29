package com.candle.common.config;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum CandleInterval {

    ONE_SECOND  ("1s",   1),
    FIVE_SECONDS("5s",   5),
    ONE_MINUTE  ("1m",   60),
    FIFTEEN_MIN ("15m",  900),
    ONE_HOUR    ("1h",   3600);

    private final String label;
    private final long   durationSeconds;

    CandleInterval(String label, long durationSeconds) {
        this.label           = label;
        this.durationSeconds = durationSeconds;
    }

    public String getLabel()           { return label; }
    public long   getDurationSeconds() { return durationSeconds; }

    /**
     * Align a unix-second timestamp to the start of its window.
     * e.g. timestamp=1620000075, interval=1m → 1620000060
     */
    public long windowStart(long timestampSeconds) {
        return (timestampSeconds / durationSeconds) * durationSeconds;
    }

    // Lookup map for fast resolution from label string
    private static final Map<String, CandleInterval> BY_LABEL =
            Arrays.stream(values())
                  .collect(Collectors.toMap(CandleInterval::getLabel, Function.identity()));

    public static CandleInterval fromLabel(String label) {
        CandleInterval interval = BY_LABEL.get(label);
        if (interval == null) {
            throw new IllegalArgumentException("Unknown interval: " + label
                    + ". Supported: " + BY_LABEL.keySet());
        }
        return interval;
    }

    /**
     * Returns the PostgreSQL interval literal used in time_bucket() queries.
     * e.g. CandleInterval.ONE_MINUTE.toPgInterval() → "1 minute"
     */
    public String toPgInterval() {
        return switch (this) {
            case ONE_SECOND   -> "1 second";
            case FIVE_SECONDS -> "5 seconds";
            case ONE_MINUTE   -> "1 minute";
            case FIFTEEN_MIN  -> "15 minutes";
            case ONE_HOUR     -> "1 hour";
        };
    }

    @Override
    public String toString() { return label; }
}

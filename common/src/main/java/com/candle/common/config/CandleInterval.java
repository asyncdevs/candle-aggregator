package com.candle.common.config;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * All supported candle intervals.
 *
 * Each interval knows its duration in seconds — used by:
 *   - stream-ingestion to determine window boundaries
 *   - history-api to enumerate timestamps between from/to
 *   - Redis key construction
 */
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

    @Override
    public String toString() { return label; }
}

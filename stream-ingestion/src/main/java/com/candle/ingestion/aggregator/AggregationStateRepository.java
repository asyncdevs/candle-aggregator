package com.candle.ingestion.aggregator;

import com.candle.common.config.CandleInterval;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AggregationStateRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Atomically checks whether the aggregation window for (symbol, interval) has advanced.
     *
     * - First call for a symbol+interval: inserts initial state, returns empty (nothing to close yet).
     * - Same window: no-op, returns empty.
     * - New window detected: updates state, returns the OLD window start (the one just closed).
     *
     * Uses SELECT FOR UPDATE to serialise concurrent Kafka consumer threads
     * processing the same symbol.
     */
    public Optional<Long> tryAdvanceWindow(String symbol, CandleInterval interval, long newWindowStart) {

        // Try to insert — handles first-ever tick for this symbol+interval
        int inserted = jdbcTemplate.update("""
                INSERT INTO candle_aggregation_state (symbol, interval, window_start)
                VALUES (?, ?, to_timestamp(?))
                ON CONFLICT (symbol, interval) DO NOTHING
                """, symbol, interval.getLabel(), newWindowStart);

        if (inserted > 0) {
            return Optional.empty(); // First time — no prior window to close
        }

        // Lock the row so concurrent consumers don't double-aggregate
        List<Long> rows = jdbcTemplate.queryForList("""
                SELECT EXTRACT(EPOCH FROM window_start)::bigint
                FROM candle_aggregation_state
                WHERE symbol = ? AND interval = ?
                FOR UPDATE
                """, Long.class, symbol, interval.getLabel());

        if (rows.isEmpty()) return Optional.empty();

        long existingWindowStart = rows.get(0);

        if (newWindowStart <= existingWindowStart) {
            return Optional.empty(); // Still in the same window
        }

        // Advance to the new window and return the closed window's start
        jdbcTemplate.update("""
                UPDATE candle_aggregation_state
                SET window_start = to_timestamp(?)
                WHERE symbol = ? AND interval = ?
                """, newWindowStart, symbol, interval.getLabel());

        return Optional.of(existingWindowStart);
    }
}

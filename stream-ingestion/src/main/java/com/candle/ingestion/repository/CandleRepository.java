package com.candle.ingestion.repository;

import com.candle.common.config.CandleInterval;
import com.candle.common.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CandleRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Upserts a single tick into the 1s candle for that second.
     *
     * open  — set only on first insert; never overwritten (first tick in the second).
     * high  — running maximum.
     * low   — running minimum.
     * close — last tick wins (overwritten every time).
     * volume— incremented by 1 per tick.
     */
    public void upsertTick(String symbol, long tickSeconds, double midPrice) {
        jdbcTemplate.update("""
                INSERT INTO candles (symbol, interval, time, open, high, low, close, volume)
                VALUES (?, '1s', to_timestamp(?), ?, ?, ?, ?, 1)
                ON CONFLICT (symbol, interval, time) DO UPDATE SET
                    high   = GREATEST(candles.high,  EXCLUDED.high),
                    low    = LEAST(candles.low,    EXCLUDED.low),
                    close  = EXCLUDED.close,
                    volume = candles.volume + 1
                """,
                symbol, tickSeconds, midPrice, midPrice, midPrice, midPrice);
    }

    /**
     * Aggregates source-interval candles in [windowStart, windowEnd) into one target-interval candle.
     *
     * Uses TimescaleDB first()/last() aggregates to pick OPEN from the earliest source
     * candle and CLOSE from the latest — preserving price continuity across intervals.
     *
     * Returns empty if there are no source candles in the window (gap in data).
     */
    public Optional<Candle> aggregate(String symbol, CandleInterval target,
                                      CandleInterval source, long windowStart, long windowEnd) {
        String sql = """
                WITH src AS (
                    SELECT
                        first(open,  time) AS open,
                        MAX(high)          AS high,
                        MIN(low)           AS low,
                        last(close, time)  AS close,
                        SUM(volume)        AS volume,
                        COUNT(*)           AS cnt
                    FROM candles
                    WHERE symbol   = ?
                      AND interval = ?
                      AND time >= to_timestamp(?)
                      AND time  < to_timestamp(?)
                )
                INSERT INTO candles (symbol, interval, time, open, high, low, close, volume)
                SELECT ?, ?, to_timestamp(?), open, high, low, close, volume
                FROM src
                WHERE cnt > 0
                ON CONFLICT (symbol, interval, time) DO UPDATE SET
                    high   = GREATEST(candles.high, EXCLUDED.high),
                    low    = LEAST(candles.low,    EXCLUDED.low),
                    close  = EXCLUDED.close,
                    volume = EXCLUDED.volume
                RETURNING
                    EXTRACT(EPOCH FROM time)::bigint AS time,
                    symbol, interval, open, high, low, close, volume
                """;

        List<Candle> result = jdbcTemplate.query(sql,
                (rs, row) -> new Candle(
                        rs.getLong("time"),
                        rs.getString("symbol"),
                        rs.getString("interval"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("volume")),
                symbol, source.getLabel(), windowStart, windowEnd,
                symbol, target.getLabel(), windowEnd);

        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public List<Candle> findBySymbolAndIntervalBetween(
            String symbol, String interval, long fromSeconds, long toSeconds) {
        return jdbcTemplate.query("""
                SELECT symbol, interval,
                       EXTRACT(EPOCH FROM time)::bigint AS time,
                       open, high, low, close, volume
                FROM candles
                WHERE symbol = ? AND interval = ?
                  AND time >= to_timestamp(?) AND time <= to_timestamp(?)
                ORDER BY time ASC
                """,
                (rs, row) -> new Candle(
                        rs.getLong("time"),
                        rs.getString("symbol"),
                        rs.getString("interval"),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("volume")),
                symbol, interval, fromSeconds, toSeconds);
    }
}

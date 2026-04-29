package com.candle.api.repository;

import com.candle.common.config.CandleInterval;
import com.candle.common.model.BidAskEvent;
import com.candle.common.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Tick persistence and on-the-fly OHLCV aggregation via TimescaleDB.
 *
 * <h3>Insert</h3>
 * Each raw BidAsk tick is written as a single row into the 'ticks' hypertable.
 * TimescaleDB auto-partitions by time — no manual partitioning logic needed.
 *
 * <h3>Query</h3>
 * {@code time_bucket(interval, time)} groups ticks into OHLCV candles entirely
 * inside the database.  {@code first()} and {@code last()} are TimescaleDB
 * aggregate functions that preserve the chronological open/close price without
 * an ORDER BY, making aggregation efficient even over millions of rows.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TickRepository {

    private final JdbcTemplate jdbcTemplate;

    // ── Write ────────────────────────────────────────────────────────────────

    /**
     * Inserts one raw tick row.
     *
     * {@code to_timestamp(ms / 1000.0)} converts epoch-milliseconds to
     * TIMESTAMPTZ, preserving sub-second precision for the 'ticks' hypertable.
     */
    public void insertTick(BidAskEvent event) {
        jdbcTemplate.update(
            "INSERT INTO ticks (time, symbol, bid, ask, mid_price) " +
            "VALUES (to_timestamp(?), ?, ?, ?, ?)",
            event.timestamp() / 1000.0,
            event.symbol(),
            event.bid(),
            event.ask(),
            event.midPrice()
        );
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Aggregates raw ticks into OHLCV candles using TimescaleDB's time_bucket().
     *
     * @param symbol        trading pair e.g. "BTC-USD"
     * @param interval      candle size; drives the PostgreSQL interval literal
     * @param fromEpochSec  inclusive window start (unix seconds)
     * @param toEpochSec    exclusive window end   (unix seconds)
     * @return list of fully aggregated candles ordered by bucket time ascending
     */
    public List<Candle> queryCandles(String symbol,
                                     CandleInterval interval,
                                     long fromEpochSec,
                                     long toEpochSec) {

        // EXTRACT(EPOCH …)::BIGINT converts the TIMESTAMPTZ bucket back to
        // unix-epoch-seconds so the Candle record stays a plain long.
        String sql =
            "SELECT " +
            "  EXTRACT(EPOCH FROM time_bucket(CAST(? AS INTERVAL), time))::BIGINT AS bucket_epoch, " +
            "  first(mid_price, time)  AS open,   " +
            "  max(mid_price)          AS high,   " +
            "  min(mid_price)          AS low,    " +
            "  last(mid_price, time)   AS close,  " +
            "  count(*)                AS volume  " +
            "FROM ticks " +
            "WHERE symbol = ? " +
            "  AND time >= to_timestamp(?) " +
            "  AND time <  to_timestamp(?) " +
            "GROUP BY bucket_epoch " +
            "ORDER BY bucket_epoch ASC";

        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new Candle(
                rs.getLong("bucket_epoch"),
                symbol,
                interval.getLabel(),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getLong("volume")
            ),
            interval.toPgInterval(),
            symbol,
            (double) fromEpochSec,
            (double) toEpochSec
        );
    }
}

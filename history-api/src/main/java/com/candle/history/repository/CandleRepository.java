package com.candle.history.repository;

import com.candle.common.model.Candle;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CandleRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Candle> findBySymbolAndIntervalBetween(
            String symbol, String interval, long fromSeconds, long toSeconds) {
        String sql = """
            SELECT symbol, interval,
                   EXTRACT(EPOCH FROM time)::bigint AS time,
                   open, high, low, close, volume
            FROM candles
            WHERE symbol = ? AND interval = ?
              AND time >= to_timestamp(?) AND time <= to_timestamp(?)
            ORDER BY time ASC
            """;
        return jdbcTemplate.query(sql,
            (rs, row) -> new Candle(
                rs.getLong("time"),
                rs.getString("symbol"),
                rs.getString("interval"),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getLong("volume")
            ),
            symbol, interval, fromSeconds, toSeconds
        );
    }
}

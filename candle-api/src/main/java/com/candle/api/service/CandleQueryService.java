package com.candle.api.service;

import com.candle.api.repository.TickRepository;
import com.candle.common.config.CandleInterval;
import com.candle.common.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Delegates OHLCV aggregation entirely to TimescaleDB's time_bucket().
 *
 * No cache, no pre-computation — TimescaleDB hypertable chunk exclusion
 * makes range queries fast at this data volume without extra layers.
 *
 * The returned list feeds directly into {@link com.candle.api.model.HistoryResponse#ok}
 * which converts it to the TradingView UDF parallel-array format.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleQueryService {

    private final TickRepository tickRepository;

    /**
     * Returns OHLCV candles aggregated by TimescaleDB for the given parameters.
     *
     * @param symbol        trading pair  e.g. "BTC-USD"
     * @param intervalLabel candle size   e.g. "1m", "5s", "1h"
     * @param fromEpochSec  inclusive range start (unix seconds)
     * @param toEpochSec    exclusive range end   (unix seconds)
     * @return ordered list of candles; empty list if no ticks exist in range
     * @throws IllegalArgumentException if intervalLabel is not a supported interval
     */
    public List<Candle> getCandles(String symbol,
                                   String intervalLabel,
                                   long fromEpochSec,
                                   long toEpochSec) {

        // Validates the label — throws IllegalArgumentException on unknown value
        CandleInterval interval = CandleInterval.fromLabel(intervalLabel);

        log.info("[CANDLE-API] QUERY  symbol={}  interval={}  from={}  to={}  pg_interval='{}'",
            symbol, intervalLabel, fromEpochSec, toEpochSec, interval.toPgInterval());

        List<Candle> candles = tickRepository.queryCandles(
            symbol, interval, fromEpochSec, toEpochSec);

        if (candles.isEmpty()) {
            log.info("[CANDLE-API] NO_DATA  symbol={}  interval={}  from={}  to={}",
                symbol, intervalLabel, fromEpochSec, toEpochSec);
        } else {
            log.info("[CANDLE-API] RESULT  symbol={}  interval={}  bars={}  first_t={}  last_t={}",
                symbol, intervalLabel, candles.size(),
                candles.getFirst().time(), candles.getLast().time());
        }

        return candles;
    }
}

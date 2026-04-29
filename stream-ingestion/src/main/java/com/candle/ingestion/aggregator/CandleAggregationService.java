package com.candle.ingestion.aggregator;

import com.candle.common.config.CandleInterval;
import com.candle.common.model.BidAskEvent;
import com.candle.common.model.Candle;
import com.candle.ingestion.publisher.CandleEventPublisher;
import com.candle.ingestion.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandleAggregationService {

    private final CandleRepository           candleRepository;
    private final AggregationStateRepository stateRepository;
    private final CandleEventPublisher        eventPublisher;

    // Aggregation hierarchy — each interval is built from its source
    static final List<CandleInterval> HIGHER_INTERVALS =
        List.of(CandleInterval.FIVE_SECONDS, CandleInterval.ONE_MINUTE,
                CandleInterval.FIFTEEN_MIN,  CandleInterval.ONE_HOUR);

    static final Map<CandleInterval, CandleInterval> SOURCE_INTERVAL = Map.of(
        CandleInterval.FIVE_SECONDS, CandleInterval.ONE_SECOND,
        CandleInterval.ONE_MINUTE,   CandleInterval.FIVE_SECONDS,
        CandleInterval.FIFTEEN_MIN,  CandleInterval.ONE_MINUTE,
        CandleInterval.ONE_HOUR,     CandleInterval.FIFTEEN_MIN
    );

    /**
     * Tick pipeline — only persists the raw 1s candle.
     * Higher-interval aggregation is handled by the scheduler.
     */
    @Transactional
    public void process(BidAskEvent event) {
        candleRepository.upsertTick(
            event.symbol(), event.timestampSeconds(), event.midPrice());
    }

    /**
     * Called by {@link CandleAggregationScheduler} on every 5-second clock tick.
     *
     * Checks every higher interval for the given symbol:
     *   - If the current wall-clock window is ahead of stored state → the previous window closed.
     *   - Aggregates that closed window from its source interval and publishes the candle event.
     *
     * Cascade order (5s → 1m → 15m → 1h) ensures each source candle is written
     * before the next level reads it, all within a single transaction.
     */
    @Transactional
    public void aggregateHigherIntervals(String symbol, long nowSeconds) {
        for (CandleInterval interval : HIGHER_INTERVALS) {
            long newWindowStart = interval.windowStart(nowSeconds);

            Optional<Long> closedWindowStart =
                stateRepository.tryAdvanceWindow(symbol, interval, newWindowStart);

            closedWindowStart.ifPresent(ws -> {
                long windowEnd = ws + interval.getDurationSeconds();
                CandleInterval source = SOURCE_INTERVAL.get(interval);

                Optional<Candle> candle =
                    candleRepository.aggregate(symbol, interval, source, ws, windowEnd);

                candle.ifPresent(c -> {
                    log.info("[SCHEDULER] Closed {} {} windowEnd={} o={} h={} l={} c={} v={}",
                        c.symbol(), c.interval(), c.time(),
                        c.open(), c.high(), c.low(), c.close(), c.volume());
                    eventPublisher.publish(c);
                });
            });
        }
    }
}

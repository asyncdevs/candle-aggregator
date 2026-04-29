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
    private static final List<CandleInterval> HIGHER_INTERVALS =
        List.of(CandleInterval.FIVE_SECONDS, CandleInterval.ONE_MINUTE,
                CandleInterval.FIFTEEN_MIN,  CandleInterval.ONE_HOUR);

    private static final Map<CandleInterval, CandleInterval> SOURCE_INTERVAL = Map.of(
        CandleInterval.FIVE_SECONDS, CandleInterval.ONE_SECOND,
        CandleInterval.ONE_MINUTE,   CandleInterval.FIVE_SECONDS,
        CandleInterval.FIFTEEN_MIN,  CandleInterval.ONE_MINUTE,
        CandleInterval.ONE_HOUR,     CandleInterval.FIFTEEN_MIN
    );

    /**
     * Full processing pipeline for one BidAskEvent, wrapped in a single transaction:
     *
     *   1. Upsert the 1s candle from this tick's mid-price.
     *   2. For each higher interval (5s → 1m → 15m → 1h):
     *        - Check whether the current window has advanced past the stored state.
     *        - If yes, aggregate the just-closed window from its source interval and publish.
     *
     * Ordering (5s before 1m, etc.) ensures the source candle is always stored
     * before the next level tries to aggregate it.
     */
    @Transactional
    public void process(BidAskEvent event) {
        long   tickSeconds = event.timestampSeconds();
        double midPrice    = event.midPrice();
        String symbol      = event.symbol();

        // Step 1 — raw tick into 1s candle
        candleRepository.upsertTick(symbol, tickSeconds, midPrice);

        // Step 2 — cascade aggregation for higher intervals
        for (CandleInterval interval : HIGHER_INTERVALS) {
            long newWindowStart = interval.windowStart(tickSeconds);

            Optional<Long> closedWindowStart =
                stateRepository.tryAdvanceWindow(symbol, interval, newWindowStart);

            closedWindowStart.ifPresent(ws -> {
                long windowEnd = ws + interval.getDurationSeconds();
                CandleInterval source = SOURCE_INTERVAL.get(interval);

                Optional<Candle> candle =
                    candleRepository.aggregate(symbol, interval, source, ws, windowEnd);

                candle.ifPresent(c -> {
                    log.info("[INGESTION] Closed {} {} t={} o={} h={} l={} c={} v={}",
                        c.symbol(), c.interval(), c.time(),
                        c.open(), c.high(), c.low(), c.close(), c.volume());
                    eventPublisher.publish(c);
                });
            });
        }
    }
}

package com.candle.ingestion.aggregator;

import com.candle.common.config.Symbols;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Closes higher-interval candles (5s → 1m → 15m → 1h) on a fixed clock schedule,
 * independent of tick flow.
 *
 * Fires at every 5-second boundary (:00, :05, :10 … :55) using a cron expression.
 * This guarantees candles are closed even during quiet periods with no incoming ticks.
 *
 * Each symbol is aggregated in its own transaction so a failure for one symbol
 * does not roll back others.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleAggregationScheduler {

    private final CandleAggregationService aggregationService;

    /**
     * Cron: second=0/5 → fires at :00, :05, :10, :15, :20, :25, :30, :35, :40, :45, :50, :55
     */
    @Scheduled(cron = "0/5 * * * * *")
    public void closeCandles() {
        long now = Instant.now().getEpochSecond();
        log.debug("[SCHEDULER] Tick at epoch={}", now);

        for (String symbol : Symbols.ALL) {
            try {
                aggregationService.aggregateHigherIntervals(symbol, now);
            } catch (Exception ex) {
                log.error("[SCHEDULER] Aggregation failed for symbol={}: {}", symbol, ex.getMessage(), ex);
            }
        }
    }
}

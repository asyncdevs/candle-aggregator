package com.candle.adapter.producer;

import com.candle.adapter.simulator.PriceSimulator;
import com.candle.common.config.KafkaTopics;
import com.candle.common.config.Symbols;
import com.candle.common.model.BidAskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled producer — simulates an exchange tick feed.
 *
 * Fires every {@code adapter.tick-interval-ms} milliseconds (default 500 ms).
 * Produces one {@link BidAskEvent} per symbol per tick.
 * Partition key = symbol — guarantees all ticks for a symbol land on the same
 * Kafka partition and are consumed in order by the candle-api.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidAskEventProducer {

    private final KafkaTemplate<String, BidAskEvent> kafkaTemplate;
    private final PriceSimulator priceSimulator;

    @Scheduled(fixedRateString = "${adapter.tick-interval-ms:500}")
    public void publishTicks() {
        long nowMs = Instant.now().toEpochMilli();

        for (String symbol : Symbols.ALL) {
            double[] bidAsk = priceSimulator.nextBidAsk(symbol);
            BidAskEvent event = new BidAskEvent(symbol, bidAsk[0], bidAsk[1], nowMs);

            kafkaTemplate.send(KafkaTopics.RAW_BIDASK_EVENTS, symbol, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[ADAPTER] PUBLISH FAILED  symbol={}  error={}",
                            symbol, ex.getMessage());
                    } else {
                        log.info("[ADAPTER] PUBLISHED  symbol={}  bid={}  ask={}  mid={}  " +
                                 "partition={}  offset={}",
                            symbol,
                            bidAsk[0],
                            bidAsk[1],
                            String.format("%.4f", (bidAsk[0] + bidAsk[1]) / 2.0),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    }
                });
        }
    }
}

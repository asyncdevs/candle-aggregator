package com.candle.api.consumer;

import com.candle.api.repository.TickRepository;
import com.candle.common.config.KafkaTopics;
import com.candle.common.model.BidAskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes raw BidAsk tick events from Kafka and persists them to the
 * TimescaleDB 'ticks' hypertable.
 *
 * One message = one row.  No pre-aggregation, no in-memory state.
 * All OHLCV computation happens at query time via time_bucket().
 *
 * concurrency=4 (configured in KafkaConsumerConfig) — one thread per partition,
 * partitioned by symbol, so inserts for the same symbol are always sequential.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidAskEventConsumer {

    private final TickRepository tickRepository;

    @KafkaListener(
        topics = KafkaTopics.RAW_BIDASK_EVENTS,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(BidAskEvent event) {
        log.info("[CANDLE-API] TICK  symbol={}  bid={}  ask={}  mid={}  ts={}",
            event.symbol(), event.bid(), event.ask(),
            String.format("%.4f", event.midPrice()),
            event.timestampSeconds());

        tickRepository.insertTick(event);
    }
}

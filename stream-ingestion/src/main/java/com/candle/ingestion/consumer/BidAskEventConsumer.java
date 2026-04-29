package com.candle.ingestion.consumer;

import com.candle.common.config.KafkaTopics;
import com.candle.common.model.BidAskEvent;
import com.candle.ingestion.aggregator.CandleAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BidAskEventConsumer {

    private final CandleAggregationService aggregationService;

    @KafkaListener(
        topics = KafkaTopics.RAW_BIDASK_EVENTS,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(BidAskEvent event) {
        log.debug("[INGESTION] Received tick {} bid={} ask={}",
                  event.symbol(), event.bid(), event.ask());
        aggregationService.process(event);
    }
}

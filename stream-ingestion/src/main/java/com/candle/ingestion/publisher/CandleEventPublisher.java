package com.candle.ingestion.publisher;

import com.candle.common.config.KafkaTopics;
import com.candle.common.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CandleEventPublisher {

    private final KafkaTemplate<String, Candle> kafkaTemplate;

    public void publish(Candle candle) {
        kafkaTemplate.send(KafkaTopics.CANDLE_CLOSED_EVENTS, candle.symbol(), candle)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[INGESTION] Failed to publish candle event: {}", ex.getMessage());
                }
            });
    }
}

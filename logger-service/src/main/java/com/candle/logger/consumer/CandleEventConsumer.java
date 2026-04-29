package com.candle.logger.consumer;

import com.candle.common.config.KafkaTopics;
import com.candle.common.model.BidAskEvent;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.argument.StructuredArguments;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consumes every raw BidAsk tick from Kafka and emits a structured log line.
 *
 * The log line is picked up by the LogstashTcpSocketAppender (configured in
 * logback-spring.xml) and shipped as JSON to Logstash → Elasticsearch → Kibana.
 *
 * No direct Elasticsearch dependency — the entire persistence path is:
 *   Kafka → this consumer → SLF4J → Logback → Logstash TCP → ES
 *
 * This service runs in a separate consumer group ("logger-group") so it is
 * fully decoupled from the candle-api's ingestion group.
 */
@Slf4j
@Component
public class CandleEventConsumer {

    @KafkaListener(
        topics = KafkaTopics.RAW_BIDASK_EVENTS,
        groupId = "logger-group",
        containerFactory = "bidAskListenerContainerFactory"
    )
    public void consume(BidAskEvent event) {
        // StructuredArguments.keyValue() adds extra JSON fields to the Logstash
        // document alongside the standard @timestamp / level / message fields.
        log.info("[LOGGER] BidAsk tick received",
            StructuredArguments.keyValue("event_type",    "bidask_tick"),
            StructuredArguments.keyValue("symbol",        event.symbol()),
            StructuredArguments.keyValue("bid",           event.bid()),
            StructuredArguments.keyValue("ask",           event.ask()),
            StructuredArguments.keyValue("mid_price",     event.midPrice()),
            StructuredArguments.keyValue("event_ts_ms",   event.timestamp()),
            StructuredArguments.keyValue("event_ts_iso",
                Instant.ofEpochMilli(event.timestamp()).toString())
        );
    }
}

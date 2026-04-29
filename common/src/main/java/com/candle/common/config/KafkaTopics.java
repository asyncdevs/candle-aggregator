package com.candle.common.config;

/**
 * Centralised Kafka topic names.
 * All services reference these constants — never hardcode topic strings.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Raw bid/ask tick events published by the market-data-adapter. */
    public static final String RAW_BIDASK_EVENTS = "raw-bidask-events";

    /**
     * Dead-letter topic for ticks that could not be persisted after retries.
     * Spring Kafka's DeadLetterPublishingRecoverer writes here automatically.
     * Named after the Spring Kafka DLT convention: source-topic + ".DLT".
     */
    public static final String RAW_BIDASK_DLT = RAW_BIDASK_EVENTS + ".DLT";
}

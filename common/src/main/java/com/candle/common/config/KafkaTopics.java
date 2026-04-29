package com.candle.common.config;

/**
 * Centralised Kafka topic names.
 * All services reference these constants — never hardcode topic strings.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Raw tick events published by the market-data-adapter. */
    public static final String RAW_BIDASK_EVENTS = "raw-bidask-events";

    /** Closed candle events published by stream-ingestion on each window flush. */
    public static final String CANDLE_CLOSED_EVENTS = "candle-closed-events";

    /** Audit events published by history-api on each incoming query. */
    public static final String QUERY_AUDIT_EVENTS = "query-audit-events";
}

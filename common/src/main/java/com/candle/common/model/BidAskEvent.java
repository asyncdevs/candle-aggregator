package com.candle.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single raw bid/ask tick from the exchange.
 *
 * symbol    — trading pair e.g. BTC-USD
 * bid       — highest price a buyer will pay
 * ask       — lowest price a seller will accept
 * timestamp — unix epoch milliseconds from the exchange
 */
public record BidAskEvent(
        @JsonProperty("symbol")    String symbol,
        @JsonProperty("bid")       double bid,
        @JsonProperty("ask")       double ask,
        @JsonProperty("timestamp") long timestamp
) {
    /**
     * Mid-price — used as the representative price for OHLC aggregation.
     * Standard practice when only bid/ask is available (no last-trade price).
     */
    public double midPrice() {
        return (bid + ask) / 2.0;
    }

    /**
     * Timestamp in unix seconds (TimescaleDB and TradingView use seconds).
     */
    public long timestampSeconds() {
        return timestamp / 1000L;
    }
}

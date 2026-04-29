package com.candle.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Represents a single raw bid/ask tick from the exchange.
 *
 * symbol    — trading pair e.g. BTC-USD
 * bid       — highest price a buyer will pay
 * ask       — lowest price a seller will accept
 * timestamp — unix epoch milliseconds from the exchange
 *
 * BigDecimal is used for bid/ask to preserve exact decimal representation —
 * floating-point types (double/float) cannot represent many decimal fractions
 * exactly and accumulate rounding error across aggregations.
 */
public record BidAskEvent(
        @JsonProperty("symbol")    String     symbol,
        @JsonProperty("bid")       BigDecimal bid,
        @JsonProperty("ask")       BigDecimal ask,
        @JsonProperty("timestamp") long       timestamp
) {
    /**
     * Mid-price — used as the representative price for OHLC aggregation.
     * Rounded to 8 decimal places (sufficient precision for all supported instruments).
     */
    public BigDecimal midPrice() {
        return bid.add(ask).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
    }

    /**
     * Timestamp in unix seconds (TimescaleDB and TradingView use seconds).
     */
    public long timestampSeconds() {
        return timestamp / 1000L;
    }
}

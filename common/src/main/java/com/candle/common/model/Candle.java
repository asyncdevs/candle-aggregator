package com.candle.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A closed OHLC candlestick for a given symbol and interval.
 *
 * time     — window open time in unix seconds (aligns with TradingView UDF)
 * symbol   — trading pair
 * interval — candle duration e.g. "1s", "1m", "1h"
 * open     — mid-price of the first tick in the window
 * high     — highest mid-price in the window
 * low      — lowest mid-price in the window
 * close    — mid-price of the last tick in the window
 * volume   — number of ticks received in the window (synthetic volume)
 */
public record Candle(
        @JsonProperty("time")     long time,
        @JsonProperty("symbol")   String symbol,
        @JsonProperty("interval") String interval,
        @JsonProperty("open")     double open,
        @JsonProperty("high")     double high,
        @JsonProperty("low")      double low,
        @JsonProperty("close")    double close,
        @JsonProperty("volume")   long volume
) {
    /**
     * Redis cache key for this candle.
     * Format: candle:{symbol}:{interval}:{time}
     * Example: candle:BTC-USD:1m:1620000060
     */
    public String cacheKey() {
        return "candle:" + symbol + ":" + interval + ":" + time;
    }
}

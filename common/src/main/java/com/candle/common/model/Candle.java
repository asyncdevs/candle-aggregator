package com.candle.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * An OHLCV candlestick for a given symbol and interval.
 *
 * time     — bucket start time in unix seconds
 * symbol   — trading pair  e.g. BTC-USD
 * interval — candle duration label e.g. "1s", "5s", "1m", "15m", "1h"
 * open     — mid-price of the first tick in the window
 * high     — highest mid-price in the window
 * low      — lowest mid-price in the window
 * close    — mid-price of the last tick in the window
 * volume   — number of ticks received in the window (synthetic volume)
 *
 * Produced on-the-fly by TimescaleDB time_bucket() — never stored pre-aggregated.
 */
public record Candle(
        @JsonProperty("time")     long   time,
        @JsonProperty("symbol")   String symbol,
        @JsonProperty("interval") String interval,
        @JsonProperty("open")     double open,
        @JsonProperty("high")     double high,
        @JsonProperty("low")      double low,
        @JsonProperty("close")    double close,
        @JsonProperty("volume")   long   volume
) {}

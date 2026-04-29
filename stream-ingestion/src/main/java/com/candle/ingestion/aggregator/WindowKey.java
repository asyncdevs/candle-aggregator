package com.candle.ingestion.aggregator;

import com.candle.common.config.CandleInterval;

public record WindowKey(String symbol, CandleInterval interval) {}

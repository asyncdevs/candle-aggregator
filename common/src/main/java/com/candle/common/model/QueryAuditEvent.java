package com.candle.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QueryAuditEvent(
    @JsonProperty("symbol")      String symbol,
    @JsonProperty("interval")    String interval,
    @JsonProperty("from")        long from,
    @JsonProperty("to")          long to,
    @JsonProperty("resultCount") int resultCount,
    @JsonProperty("queriedAt")   long queriedAt
) {}

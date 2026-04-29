package com.candle.history.model;

import com.candle.common.model.Candle;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record HistoryResponse(
    @JsonProperty("s") String   status,
    @JsonProperty("t") long[]   time,
    @JsonProperty("o") double[] open,
    @JsonProperty("h") double[] high,
    @JsonProperty("l") double[] low,
    @JsonProperty("c") double[] close,
    @JsonProperty("v") long[]   volume
) {
    public static HistoryResponse from(List<Candle> candles) {
        if (candles.isEmpty()) {
            return noData();
        }
        int n = candles.size();
        long[]   t = new long[n];
        double[] o = new double[n];
        double[] h = new double[n];
        double[] l = new double[n];
        double[] c = new double[n];
        long[]   v = new long[n];

        for (int i = 0; i < n; i++) {
            Candle candle = candles.get(i);
            t[i] = candle.time();
            o[i] = candle.open();
            h[i] = candle.high();
            l[i] = candle.low();
            c[i] = candle.close();
            v[i] = candle.volume();
        }
        return new HistoryResponse("ok", t, o, h, l, c, v);
    }

    public static HistoryResponse noData() {
        return new HistoryResponse("no_data",
            new long[0], new double[0], new double[0],
            new double[0], new double[0], new long[0]);
    }
}

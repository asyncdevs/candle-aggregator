package com.candle.api.model;

import com.candle.common.model.Candle;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TradingView UDF (Universal Data Feed) compatible history response.
 *
 * The frontend charting library expects OHLCV data as parallel arrays,
 * not an array of objects — this avoids repeated field-name overhead and
 * matches the TradingView Lightweight Charts / UDF contract exactly.
 *
 * <pre>
 * {
 *   "s": "ok",
 *   "t": [1620000000, 1620000060, ...],   // unix seconds
 *   "o": [29500.5,    29501.0,    ...],   // open
 *   "h": [29510.0,    29505.0,    ...],   // high
 *   "l": [29490.0,    29500.0,    ...],   // low
 *   "c": [29505.0,    29502.0,    ...],   // close
 *   "v": [10,         8,          ...]    // volume (tick count)
 * }
 * </pre>
 *
 * When there are no candles in the requested range:
 * <pre>{ "s": "no_data" }</pre>
 *
 * On a validation error:
 * <pre>{ "s": "error", "errmsg": "Unknown interval: 2x. Supported: ..." }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryResponse(

        @JsonProperty("s")      String   s,
        @JsonProperty("t")      long[]   t,
        @JsonProperty("o")      double[] o,
        @JsonProperty("h")      double[] h,
        @JsonProperty("l")      double[] l,
        @JsonProperty("c")      double[] c,
        @JsonProperty("v")      long[]   v,
        @JsonProperty("errmsg") String   errmsg

) {

    // ── Factory methods ──────────────────────────────────────────────────────

    /**
     * Successful response — converts a list of candles into parallel arrays.
     * Returns {@link #noData()} when the list is empty.
     */
    public static HistoryResponse ok(List<Candle> candles) {
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

        return new HistoryResponse("ok", t, o, h, l, c, v, null);
    }

    /**
     * No data available for the requested symbol/interval/range.
     * TradingView renders a gap on the chart when it receives this.
     */
    public static HistoryResponse noData() {
        return new HistoryResponse("no_data", null, null, null, null, null, null, null);
    }

    /**
     * Validation or server error.
     * @param message human-readable description returned in "errmsg"
     */
    public static HistoryResponse error(String message) {
        return new HistoryResponse("error", null, null, null, null, null, null, message);
    }
}

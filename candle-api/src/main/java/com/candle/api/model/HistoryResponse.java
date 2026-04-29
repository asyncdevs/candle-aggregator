package com.candle.api.model;

import com.candle.common.model.Candle;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * TradingView UDF (Universal Data Feed) compatible history response.
 *
 * The frontend charting library expects OHLCV data as parallel arrays,
 * not an array of objects — this avoids repeated field-name overhead and
 * matches the TradingView Lightweight Charts / UDF contract exactly.
 *
 * BigDecimal is used for price arrays to preserve the exact precision
 * stored in NUMERIC(19,8) database columns. Jackson serialises BigDecimal
 * as a plain JSON number without scientific notation by default.
 *
 * <pre>
 * {
 *   "s": "ok",
 *   "t": [1620000000, 1620000060, ...],   // unix seconds
 *   "o": [29500.50000000, 29501.00, ...], // open  (BigDecimal)
 *   "h": [29510.00,       29505.00, ...], // high
 *   "l": [29490.00,       29500.00, ...], // low
 *   "c": [29505.00,       29502.00, ...], // close
 *   "v": [10,             8,        ...]  // volume (tick count)
 * }
 * </pre>
 *
 * When there are no candles: <pre>{ "s": "no_data" }</pre>
 * On a validation error:     <pre>{ "s": "error", "errmsg": "..." }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryResponse(

        @JsonProperty("s")      String       s,
        @JsonProperty("t")      long[]       t,
        @JsonProperty("o")      BigDecimal[] o,
        @JsonProperty("h")      BigDecimal[] h,
        @JsonProperty("l")      BigDecimal[] l,
        @JsonProperty("c")      BigDecimal[] c,
        @JsonProperty("v")      long[]       v,
        @JsonProperty("errmsg") String       errmsg

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
        long[]       t = new long[n];
        BigDecimal[] o = new BigDecimal[n];
        BigDecimal[] h = new BigDecimal[n];
        BigDecimal[] l = new BigDecimal[n];
        BigDecimal[] c = new BigDecimal[n];
        long[]       v = new long[n];

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

    /** No data available for the requested symbol/interval/range. */
    public static HistoryResponse noData() {
        return new HistoryResponse("no_data", null, null, null, null, null, null, null);
    }

    /** Validation or server error. */
    public static HistoryResponse error(String message) {
        return new HistoryResponse("error", null, null, null, null, null, null, message);
    }
}

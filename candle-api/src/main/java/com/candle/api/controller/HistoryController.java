package com.candle.api.controller;

import com.candle.api.model.HistoryResponse;
import com.candle.api.service.CandleQueryService;
import com.candle.common.model.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * History API — serves OHLCV candle data in TradingView UDF format.
 *
 * <pre>
 * GET /history
 *   ?symbol   = BTC-USD           (required)
 *   &interval = 1s|5s|1m|15m|1h  (required)
 *   &from     = 1620000000        (unix seconds, inclusive)
 *   &to       = 1620000600        (unix seconds, exclusive)
 * </pre>
 *
 * Success response (data exists):
 * <pre>
 * {
 *   "s": "ok",
 *   "t": [1620000000, 1620000060, ...],
 *   "o": [29500.5,    29501.0,    ...],
 *   "h": [29510.0,    29505.0,    ...],
 *   "l": [29490.0,    29500.0,    ...],
 *   "c": [29505.0,    29502.0,    ...],
 *   "v": [10,         8,          ...]
 * }
 * </pre>
 *
 * No data in range:
 * <pre>{ "s": "no_data" }</pre>
 *
 * Unknown interval or other validation error (HTTP 400):
 * <pre>{ "s": "error", "errmsg": "Unknown interval: 2x. Supported: ..." }</pre>
 *
 * Health: GET /actuator/health
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final CandleQueryService queryService;

    /**
     * Returns historical OHLCV candles for the requested symbol, interval,
     * and time range in TradingView UDF parallel-array format.
     */
    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long   from,
            @RequestParam long   to) {

        log.info("[HISTORY] REQUEST  symbol={}  interval={}  from={}  to={}",
            symbol, interval, from, to);

        List<Candle> candles = queryService.getCandles(symbol, interval, from, to);
        HistoryResponse response = HistoryResponse.ok(candles);

        log.info("[HISTORY] RESPONSE  symbol={}  interval={}  status={}  bars={}",
            symbol, interval, response.s(), candles.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Handles unknown interval labels and other validation errors.
     * Returns HTTP 400 with a TradingView-compatible error body.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<HistoryResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("[HISTORY] BAD REQUEST: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(HistoryResponse.error(ex.getMessage()));
    }
}

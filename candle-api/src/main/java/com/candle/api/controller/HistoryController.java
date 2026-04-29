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
 * Validation rules:
 * <ul>
 *   <li>{@code interval} must be one of the supported labels — HTTP 400 otherwise.</li>
 *   <li>{@code from} must be strictly less than {@code to} — HTTP 400 otherwise.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final CandleQueryService queryService;

    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long   from,
            @RequestParam long   to) {

        if (from >= to) {
            String msg = String.format("'from' (%d) must be strictly less than 'to' (%d)", from, to);
            log.warn("[HISTORY] BAD REQUEST: {}", msg);
            return ResponseEntity.badRequest().body(HistoryResponse.error(msg));
        }

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

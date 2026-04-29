package com.candle.history.controller;

import com.candle.common.config.CandleInterval;
import com.candle.common.config.Symbols;
import com.candle.history.model.HistoryResponse;
import com.candle.history.service.HistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping("/history")
    public ResponseEntity<HistoryResponse> getHistory(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long from,
            @RequestParam long to) {

        if (from >= to) {
            return ResponseEntity.badRequest().build();
        }
        try {
            CandleInterval.fromLabel(interval);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (!Symbols.ALL.contains(symbol)) {
            return ResponseEntity.badRequest().build();
        }

        log.info("[HISTORY] Request symbol={} interval={} from={} to={}", symbol, interval, from, to);
        HistoryResponse response = historyService.getHistory(symbol, interval, from, to);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }
}

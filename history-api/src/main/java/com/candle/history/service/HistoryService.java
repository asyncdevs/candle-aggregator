package com.candle.history.service;

import com.candle.common.model.Candle;
import com.candle.history.model.HistoryResponse;
import com.candle.history.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final CandleRepository candleRepository;

    public HistoryResponse getHistory(String symbol, String interval, long from, long to) {
        List<Candle> candles = candleRepository.findBySymbolAndIntervalBetween(
            symbol, interval, from, to);

        log.info("[HISTORY] symbol={} interval={} from={} to={} results={}",
            symbol, interval, from, to, candles.size());

        return HistoryResponse.from(candles);
    }
}

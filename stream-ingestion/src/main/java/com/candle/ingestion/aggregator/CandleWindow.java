package com.candle.ingestion.aggregator;

import com.candle.common.config.CandleInterval;
import com.candle.common.model.Candle;
import lombok.Getter;

import java.util.concurrent.locks.ReentrantLock;

public class CandleWindow {

    @Getter
    private final String symbol;
    @Getter
    private final CandleInterval interval;
    @Getter
    private final long windowStart;

    @Getter
    private double open = 0;
    @Getter
    private double high = Double.MIN_VALUE;
    @Getter
    private double low  = Double.MAX_VALUE;
    @Getter
    private double close = 0;
    @Getter
    private long   volume = 0;
    @Getter
    private boolean initialized = false;

    private final ReentrantLock lock = new ReentrantLock();

    public CandleWindow(String symbol, CandleInterval interval, long windowStart) {
        this.symbol      = symbol;
        this.interval    = interval;
        this.windowStart = windowStart;
    }

    public void applyTick(double midPrice) {
        lock.lock();
        try {
            if (!initialized) {
                open = midPrice;
                initialized = true;
            }
            high   = Math.max(high, midPrice);
            low    = Math.min(low, midPrice);
            close  = midPrice;
            volume++;
        } finally {
            lock.unlock();
        }
    }

    public Candle toCandle() {
        lock.lock();
        try {
            return new Candle(windowStart, symbol, interval.getLabel(), open, high, low, close, volume);
        } finally {
            lock.unlock();
        }
    }

    public boolean isExpired(long currentTimeSeconds) {
        return currentTimeSeconds >= windowStart + interval.getDurationSeconds();
    }
}

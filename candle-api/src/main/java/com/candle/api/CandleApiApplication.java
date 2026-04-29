package com.candle.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Candle API — unified tick ingestion + OHLCV history service.
 *
 * Responsibilities:
 *   1. Consumes raw BidAsk events from Kafka and persists each tick to
 *      TimescaleDB's 'ticks' hypertable.
 *   2. Exposes GET /api/candles which aggregates OHLCV candles on-the-fly
 *      using TimescaleDB's time_bucket() — no pre-computation required.
 */
@SpringBootApplication
public class CandleApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CandleApiApplication.class, args);
    }
}

-- ──────────────────────────────────────────────────────────────────────────
-- Candle API — TimescaleDB schema
--
-- Raw tick table (hypertable)
-- Each row = one BidAsk event received from the market-data-adapter.
-- OHLCV aggregation is performed at query time using time_bucket().
-- ──────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS ticks (
    time      TIMESTAMPTZ      NOT NULL,
    symbol    TEXT             NOT NULL,
    bid       DOUBLE PRECISION NOT NULL,
    ask       DOUBLE PRECISION NOT NULL,
    mid_price DOUBLE PRECISION NOT NULL
);

-- Convert to TimescaleDB hypertable (partitioned by time automatically)
SELECT create_hypertable('ticks', 'time', if_not_exists => TRUE);

-- Covering index for the canonical query pattern:
--   WHERE symbol = ? AND time >= ? AND time < ?
-- The DESC order matches the typical "latest first" read pattern.
CREATE INDEX IF NOT EXISTS idx_ticks_symbol_time
    ON ticks (symbol, time DESC);

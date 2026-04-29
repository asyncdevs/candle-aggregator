-- ──────────────────────────────────────────────────────────────────────────
-- Candle API — TimescaleDB schema
--
-- Runs on every startup (spring.sql.init.mode=always).
-- Every statement is idempotent so repeated startups are safe.
-- ──────────────────────────────────────────────────────────────────────────

-- Enable TimescaleDB extension (pre-loaded in shared_preload_libraries by the
-- Docker image; this statement is idempotent via IF NOT EXISTS).
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Raw tick table (hypertable).
-- NUMERIC(19, 8) is used instead of DOUBLE PRECISION so that bid/ask/mid_price
-- values are stored with exact decimal precision — no floating-point rounding.
CREATE TABLE IF NOT EXISTS ticks (
    time      TIMESTAMPTZ   NOT NULL,
    symbol    TEXT          NOT NULL,
    bid       NUMERIC(19,8) NOT NULL,
    ask       NUMERIC(19,8) NOT NULL,
    mid_price NUMERIC(19,8) NOT NULL
);

-- Convert to TimescaleDB hypertable (partitioned by time automatically).
-- if_not_exists => TRUE makes this idempotent on subsequent startups.
SELECT create_hypertable('ticks', 'time', if_not_exists => TRUE);

-- Covering index for the canonical query pattern:
--   WHERE symbol = ? AND time >= ? AND time < ?
-- The DESC order matches the typical "latest first" read pattern.
CREATE INDEX IF NOT EXISTS idx_ticks_symbol_time
    ON ticks (symbol, time DESC);

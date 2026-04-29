CREATE TABLE IF NOT EXISTS candles (
    symbol   TEXT        NOT NULL,
    interval TEXT        NOT NULL,
    time     TIMESTAMPTZ NOT NULL,
    open     DOUBLE PRECISION NOT NULL,
    high     DOUBLE PRECISION NOT NULL,
    low      DOUBLE PRECISION NOT NULL,
    close    DOUBLE PRECISION NOT NULL,
    volume   BIGINT      NOT NULL,
    CONSTRAINT candles_pkey PRIMARY KEY (symbol, interval, time)
);

SELECT create_hypertable('candles', 'time', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_candles_symbol_interval_time
    ON candles (symbol, interval, time DESC);

-- Tracks the start of the currently open aggregation window per (symbol, interval).
-- Persisted so the service survives restarts without losing window state.
CREATE TABLE IF NOT EXISTS candle_aggregation_state (
    symbol       TEXT        NOT NULL,
    interval     TEXT        NOT NULL,
    window_start TIMESTAMPTZ NOT NULL,
    CONSTRAINT candle_aggregation_state_pkey PRIMARY KEY (symbol, interval)
);

package com.candle.common.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CandleTest {

    private static final Candle SAMPLE = new Candle(
        1620000000L, "BTC-USD", "1m",
        bd("29500"), bd("29600"), bd("29400"), bd("29550"), 10L
    );

    // ── Accessor tests ────────────────────────────────────────────────────────

    @Test
    void allAccessors_returnExpectedValues() {
        assertThat(SAMPLE.time()).isEqualTo(1620000000L);
        assertThat(SAMPLE.symbol()).isEqualTo("BTC-USD");
        assertThat(SAMPLE.interval()).isEqualTo("1m");
        assertThat(SAMPLE.open()).isEqualByComparingTo(bd("29500"));
        assertThat(SAMPLE.high()).isEqualByComparingTo(bd("29600"));
        assertThat(SAMPLE.low()).isEqualByComparingTo(bd("29400"));
        assertThat(SAMPLE.close()).isEqualByComparingTo(bd("29550"));
        assertThat(SAMPLE.volume()).isEqualTo(10L);
    }

    // ── Record semantics ──────────────────────────────────────────────────────

    @Test
    void record_equality_sameValues() {
        var other = new Candle(
            1620000000L, "BTC-USD", "1m",
            bd("29500"), bd("29600"), bd("29400"), bd("29550"), 10L);
        assertThat(SAMPLE).isEqualTo(other);
        assertThat(SAMPLE.hashCode()).isEqualTo(other.hashCode());
    }

    @Test
    void record_inequality_differentTime() {
        var other = new Candle(
            1620000060L, "BTC-USD", "1m",
            bd("29500"), bd("29600"), bd("29400"), bd("29550"), 10L);
        assertThat(SAMPLE).isNotEqualTo(other);
    }

    @Test
    void record_inequality_differentSymbol() {
        var other = new Candle(
            1620000000L, "ETH-USD", "1m",
            bd("29500"), bd("29600"), bd("29400"), bd("29550"), 10L);
        assertThat(SAMPLE).isNotEqualTo(other);
    }

    @Test
    void record_inequality_differentInterval() {
        var other = new Candle(
            1620000000L, "BTC-USD", "5m",
            bd("29500"), bd("29600"), bd("29400"), bd("29550"), 10L);
        assertThat(SAMPLE).isNotEqualTo(other);
    }

    @Test
    void record_inequality_differentHigh() {
        var other = new Candle(
            1620000000L, "BTC-USD", "1m",
            bd("29500"), bd("29700"), bd("29400"), bd("29550"), 10L);
        assertThat(SAMPLE).isNotEqualTo(other);
    }

    @Test
    void record_inequality_differentVolume() {
        var other = new Candle(
            1620000000L, "BTC-USD", "1m",
            bd("29500"), bd("29600"), bd("29400"), bd("29550"), 99L);
        assertThat(SAMPLE).isNotEqualTo(other);
    }

    // ── OHLC invariants (documented by tests) ────────────────────────────────

    @Test
    void high_isGreaterThanOrEqualToOpen() {
        assertThat(SAMPLE.high()).isGreaterThanOrEqualTo(SAMPLE.open());
    }

    @Test
    void low_isLessThanOrEqualToOpen() {
        assertThat(SAMPLE.low()).isLessThanOrEqualTo(SAMPLE.open());
    }

    @Test
    void high_isGreaterThanOrEqualToLow() {
        assertThat(SAMPLE.high()).isGreaterThanOrEqualTo(SAMPLE.low());
    }

    @Test
    void high_isGreaterThanOrEqualToClose() {
        assertThat(SAMPLE.high()).isGreaterThanOrEqualTo(SAMPLE.close());
    }

    @Test
    void low_isLessThanOrEqualToClose() {
        assertThat(SAMPLE.low()).isLessThanOrEqualTo(SAMPLE.close());
    }

    @Test
    void volume_isPositive() {
        assertThat(SAMPLE.volume()).isPositive();
    }

    // ── Flat candle (single tick in window) ──────────────────────────────────

    @Test
    void flatCandle_openEqualsClose_highEqualsLow() {
        var flat = new Candle(
            1620000000L, "BTC-USD", "1s",
            bd("29500"), bd("29500"), bd("29500"), bd("29500"), 1L);
        assertThat(flat.open()).isEqualByComparingTo(flat.close());
        assertThat(flat.high()).isEqualByComparingTo(flat.low());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}

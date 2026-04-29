package com.candle.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BidAskEventTest {

    // ── midPrice() ────────────────────────────────────────────────────────────

    @Test
    void midPrice_returnsAverageOfBidAndAsk() {
        var event = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        assertThat(event.midPrice()).isCloseTo(29050.0, within(0.001));
    }

    @Test
    void midPrice_whenBidEqualsAsk_returnsSameValue() {
        var event = new BidAskEvent("BTC-USD", 29000.0, 29000.0, 1620000000000L);
        assertThat(event.midPrice()).isEqualTo(29000.0);
    }

    @ParameterizedTest
    @CsvSource({
        "1000.0, 1002.0, 1001.0",
        "0.5,    0.6,    0.55",
        "99.99,  100.01, 100.0"
    })
    void midPrice_isAlwaysMidpointOfSpread(double bid, double ask, double expected) {
        var event = new BidAskEvent("ETH-USD", bid, ask, 0L);
        assertThat(event.midPrice()).isCloseTo(expected, within(0.0001));
    }

    @Test
    void midPrice_smallSpread_precisionPreserved() {
        var event = new BidAskEvent("XAG-USD", 28.4786, 28.4814, 0L);
        assertThat(event.midPrice()).isCloseTo(28.48, within(0.0001));
    }

    // ── timestampSeconds() ────────────────────────────────────────────────────

    @Test
    void timestampSeconds_convertsMillisToSeconds() {
        var event = new BidAskEvent("BTC-USD", 0, 0, 1620000000000L);
        assertThat(event.timestampSeconds()).isEqualTo(1620000000L);
    }

    @Test
    void timestampSeconds_truncatesSubSecondMillis() {
        var event = new BidAskEvent("BTC-USD", 0, 0, 1620000000999L);
        assertThat(event.timestampSeconds()).isEqualTo(1620000000L);
    }

    @Test
    void timestampSeconds_returnsZeroForZeroTimestamp() {
        var event = new BidAskEvent("BTC-USD", 0, 0, 0L);
        assertThat(event.timestampSeconds()).isZero();
    }

    @Test
    void timestampSeconds_500msOffset_roundsDown() {
        var event = new BidAskEvent("BTC-USD", 0, 0, 1620000000500L);
        assertThat(event.timestampSeconds()).isEqualTo(1620000000L);
    }

    // ── Record semantics ──────────────────────────────────────────────────────

    @Test
    void record_equality_worksForIdenticalValues() {
        var e1 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    void record_inequality_whenSymbolDiffers() {
        var e1 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        var e2 = new BidAskEvent("ETH-USD", 29000.0, 29100.0, 1620000000000L);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void record_inequality_whenBidDiffers() {
        var e1 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", 29001.0, 29100.0, 1620000000000L);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void record_inequality_whenAskDiffers() {
        var e1 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", 29000.0, 29101.0, 1620000000000L);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void record_inequality_whenTimestampDiffers() {
        var e1 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000001L);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void toString_containsAllFields() {
        var event = new BidAskEvent("BTC-USD", 29000.0, 29100.0, 1620000000000L);
        var str = event.toString();
        assertThat(str)
            .contains("BTC-USD")
            .contains("29000.0")
            .contains("29100.0")
            .contains("1620000000000");
    }

    // ── Accessor passthrough ──────────────────────────────────────────────────

    @Test
    void accessors_returnConstructorValues() {
        var event = new BidAskEvent("XAU-USD", 2295.5, 2304.5, 1700000000000L);
        assertThat(event.symbol()).isEqualTo("XAU-USD");
        assertThat(event.bid()).isEqualTo(2295.5);
        assertThat(event.ask()).isEqualTo(2304.5);
        assertThat(event.timestamp()).isEqualTo(1700000000000L);
    }
}

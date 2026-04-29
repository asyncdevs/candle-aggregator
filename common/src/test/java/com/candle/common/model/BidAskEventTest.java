package com.candle.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BidAskEventTest {

    // ── midPrice() ────────────────────────────────────────────────────────────

    @Test
    void midPrice_returnsAverageOfBidAndAsk() {
        var event = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        assertThat(event.midPrice()).isEqualByComparingTo(bd("29050"));
    }

    @Test
    void midPrice_whenBidEqualsAsk_returnsSameValue() {
        var event = new BidAskEvent("BTC-USD", bd("29000"), bd("29000"), 1620000000000L);
        assertThat(event.midPrice()).isEqualByComparingTo(bd("29000"));
    }

    @ParameterizedTest
    @CsvSource({
        "1000.00, 1002.00, 1001.00",
        "0.50,    0.60,    0.55",
        "99.99,   100.01,  100.00"
    })
    void midPrice_isAlwaysMidpointOfSpread(String bidStr, String askStr, String expectedStr) {
        var event = new BidAskEvent("ETH-USD", new BigDecimal(bidStr), new BigDecimal(askStr), 0L);
        assertThat(event.midPrice()).isEqualByComparingTo(new BigDecimal(expectedStr));
    }

    @Test
    void midPrice_smallSpread_precisionPreserved() {
        var event = new BidAskEvent("XAG-USD", bd("28.4786"), bd("28.4814"), 0L);
        // (28.4786 + 28.4814) / 2 = 28.48
        assertThat(event.midPrice()).isEqualByComparingTo(bd("28.48"));
    }

    @Test
    void midPrice_returnsEightDecimalPlacePrecision() {
        var event = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 0L);
        // scale should be 8
        assertThat(event.midPrice().scale()).isEqualTo(8);
    }

    // ── timestampSeconds() ────────────────────────────────────────────────────

    @Test
    void timestampSeconds_convertsMillisToSeconds() {
        var event = new BidAskEvent("BTC-USD", bd("1"), bd("1"), 1620000000000L);
        assertThat(event.timestampSeconds()).isEqualTo(1620000000L);
    }

    @Test
    void timestampSeconds_truncatesSubSecondMillis() {
        var event = new BidAskEvent("BTC-USD", bd("1"), bd("1"), 1620000000999L);
        assertThat(event.timestampSeconds()).isEqualTo(1620000000L);
    }

    @Test
    void timestampSeconds_returnsZeroForZeroTimestamp() {
        var event = new BidAskEvent("BTC-USD", bd("1"), bd("1"), 0L);
        assertThat(event.timestampSeconds()).isZero();
    }

    @Test
    void timestampSeconds_500msOffset_roundsDown() {
        var event = new BidAskEvent("BTC-USD", bd("1"), bd("1"), 1620000000500L);
        assertThat(event.timestampSeconds()).isEqualTo(1620000000L);
    }

    // ── Record semantics ──────────────────────────────────────────────────────

    @Test
    void record_equality_worksForIdenticalValues() {
        var e1 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    void record_inequality_whenSymbolDiffers() {
        var e1 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        var e2 = new BidAskEvent("ETH-USD", bd("29000"), bd("29100"), 1620000000000L);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void record_inequality_whenTimestampDiffers() {
        var e1 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000001L);
        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void toString_containsAllFields() {
        var event = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        var str = event.toString();
        assertThat(str)
            .contains("BTC-USD")
            .contains("29000")
            .contains("29100")
            .contains("1620000000000");
    }

    @Test
    void accessors_returnConstructorValues() {
        var event = new BidAskEvent("XAU-USD", bd("2295.50"), bd("2304.50"), 1700000000000L);
        assertThat(event.symbol()).isEqualTo("XAU-USD");
        assertThat(event.bid()).isEqualByComparingTo(bd("2295.50"));
        assertThat(event.ask()).isEqualByComparingTo(bd("2304.50"));
        assertThat(event.timestamp()).isEqualTo(1700000000000L);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}

package com.candle.api.model;

import com.candle.common.model.Candle;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryResponseTest {

    // ── ok() — with data ─────────────────────────────────────────────────────

    @Test
    void ok_withSingleCandle_statusIsOk() {
        var response = HistoryResponse.ok(List.of(sampleCandle(1620000000L)));
        assertThat(response.s()).isEqualTo("ok");
    }

    @Test
    void ok_withSingleCandle_populatesAllArraysCorrectly() {
        var candle = new Candle(1620000000L, "BTC-USD", "1m", 29500.0, 29600.0, 29400.0, 29550.0, 10L);
        var response = HistoryResponse.ok(List.of(candle));

        assertThat(response.t()).containsExactly(1620000000L);
        assertThat(response.o()).containsExactly(29500.0);
        assertThat(response.h()).containsExactly(29600.0);
        assertThat(response.l()).containsExactly(29400.0);
        assertThat(response.c()).containsExactly(29550.0);
        assertThat(response.v()).containsExactly(10L);
    }

    @Test
    void ok_withMultipleCandles_preservesOrder() {
        var c1 = new Candle(1620000000L, "BTC-USD", "1m", 29500.0, 29600.0, 29400.0, 29550.0, 10L);
        var c2 = new Candle(1620000060L, "BTC-USD", "1m", 29550.0, 29700.0, 29500.0, 29680.0, 8L);
        var c3 = new Candle(1620000120L, "BTC-USD", "1m", 29680.0, 29800.0, 29650.0, 29770.0, 14L);
        var response = HistoryResponse.ok(List.of(c1, c2, c3));

        assertThat(response.t()).containsExactly(1620000000L, 1620000060L, 1620000120L);
        assertThat(response.o()).containsExactly(29500.0, 29550.0, 29680.0);
        assertThat(response.h()).containsExactly(29600.0, 29700.0, 29800.0);
        assertThat(response.l()).containsExactly(29400.0, 29500.0, 29650.0);
        assertThat(response.c()).containsExactly(29550.0, 29680.0, 29770.0);
        assertThat(response.v()).containsExactly(10L, 8L, 14L);
    }

    @Test
    void ok_withCandles_errmsgIsNull() {
        var response = HistoryResponse.ok(List.of(sampleCandle(1620000000L)));
        assertThat(response.errmsg()).isNull();
    }

    @Test
    void ok_allArraysHaveSameLength() {
        var candles = List.of(
            sampleCandle(1620000000L),
            sampleCandle(1620000060L),
            sampleCandle(1620000120L)
        );
        var response = HistoryResponse.ok(candles);

        int n = candles.size();
        assertThat(response.t()).hasSize(n);
        assertThat(response.o()).hasSize(n);
        assertThat(response.h()).hasSize(n);
        assertThat(response.l()).hasSize(n);
        assertThat(response.c()).hasSize(n);
        assertThat(response.v()).hasSize(n);
    }

    // ── ok() — empty list delegates to noData ────────────────────────────────

    @Test
    void ok_withEmptyList_returnsNoDataStatus() {
        var response = HistoryResponse.ok(List.of());
        assertThat(response.s()).isEqualTo("no_data");
    }

    @Test
    void ok_withEmptyList_allArraysNull() {
        var response = HistoryResponse.ok(List.of());
        assertThat(response.t()).isNull();
        assertThat(response.o()).isNull();
        assertThat(response.h()).isNull();
        assertThat(response.l()).isNull();
        assertThat(response.c()).isNull();
        assertThat(response.v()).isNull();
    }

    // ── noData() ─────────────────────────────────────────────────────────────

    @Test
    void noData_statusIsNoData() {
        assertThat(HistoryResponse.noData().s()).isEqualTo("no_data");
    }

    @Test
    void noData_allArraysAndErrmsgAreNull() {
        var r = HistoryResponse.noData();
        assertThat(r.t()).isNull();
        assertThat(r.o()).isNull();
        assertThat(r.h()).isNull();
        assertThat(r.l()).isNull();
        assertThat(r.c()).isNull();
        assertThat(r.v()).isNull();
        assertThat(r.errmsg()).isNull();
    }

    // ── error() ──────────────────────────────────────────────────────────────

    @Test
    void error_statusIsError() {
        assertThat(HistoryResponse.error("some error").s()).isEqualTo("error");
    }

    @Test
    void error_populatesErrmsg() {
        var msg = "Unknown interval: 2x. Supported: [1s, 5s, 1m, 15m, 1h]";
        assertThat(HistoryResponse.error(msg).errmsg()).isEqualTo(msg);
    }

    @Test
    void error_allArraysAreNull() {
        var r = HistoryResponse.error("oops");
        assertThat(r.t()).isNull();
        assertThat(r.o()).isNull();
        assertThat(r.h()).isNull();
        assertThat(r.l()).isNull();
        assertThat(r.c()).isNull();
        assertThat(r.v()).isNull();
    }

    @Test
    void error_emptyMessage_stillSetsStatusError() {
        var r = HistoryResponse.error("");
        assertThat(r.s()).isEqualTo("error");
        assertThat(r.errmsg()).isEmpty();
    }

    // ── Record semantics ──────────────────────────────────────────────────────

    @Test
    void noData_calledTwice_returnsEqualObjects() {
        assertThat(HistoryResponse.noData()).isEqualTo(HistoryResponse.noData());
    }

    @Test
    void error_sameMessage_returnsEqualObjects() {
        assertThat(HistoryResponse.error("x")).isEqualTo(HistoryResponse.error("x"));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Candle sampleCandle(long time) {
        return new Candle(time, "BTC-USD", "1m", 100.0, 110.0, 90.0, 105.0, 5L);
    }
}

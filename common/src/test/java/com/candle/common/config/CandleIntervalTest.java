package com.candle.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandleIntervalTest {

    // ── windowStart — boundary alignment ────────────────────────────────────

    @Test
    void windowStart_alignsTo1mBoundary() {
        long ts = 1620000075L; // 75 seconds past the minute
        assertThat(CandleInterval.ONE_MINUTE.windowStart(ts)).isEqualTo(1620000060L);
    }

    @Test
    void windowStart_alignsTo1hBoundary() {
        long ts = 1620003661L; // 1 hour + 1 second into the period
        assertThat(CandleInterval.ONE_HOUR.windowStart(ts)).isEqualTo(1620003600L);
    }

    @Test
    void windowStart_alignsTo5sBoundary() {
        long ts = 1620000007L; // 7 seconds → aligns to 5s boundary
        assertThat(CandleInterval.FIVE_SECONDS.windowStart(ts)).isEqualTo(1620000005L);
    }

    @Test
    void windowStart_alignsTo1sBoundary_isTimestampItself() {
        long ts = 1620000042L;
        assertThat(CandleInterval.ONE_SECOND.windowStart(ts)).isEqualTo(1620000042L);
    }

    @Test
    void windowStart_alignsTo15mBoundary() {
        // 1620000601 / 900 = 1800000 (integer) * 900 = 1620000000
        long ts = 1620000601L;
        assertThat(CandleInterval.FIFTEEN_MIN.windowStart(ts)).isEqualTo(1620000000L);
    }

    @Test
    void windowStart_exactBoundary_returnsSameTimestamp() {
        long ts = 1620000060L; // exactly on a 1m boundary
        assertThat(CandleInterval.ONE_MINUTE.windowStart(ts)).isEqualTo(1620000060L);
    }

    @Test
    void windowStart_zeroTimestamp_returnsZero() {
        assertThat(CandleInterval.ONE_MINUTE.windowStart(0L)).isEqualTo(0L);
    }

    @ParameterizedTest
    @CsvSource({
        "1s,  1, 1620000042, 1620000042",
        "5s,  5, 1620000007, 1620000005",
        "1m,  60, 1620000075, 1620000060",
        "15m, 900, 1620000601, 1620000000",
        "1h,  3600, 1620003661, 1620003600"
    })
    void windowStart_parameterized(String label, long durationSec, long input, long expected) {
        CandleInterval interval = CandleInterval.fromLabel(label);
        assertThat(interval.windowStart(input)).isEqualTo(expected);
    }

    // ── fromLabel ────────────────────────────────────────────────────────────

    @Test
    void fromLabel_validLabels_returnCorrectEnum() {
        assertThat(CandleInterval.fromLabel("1s")).isEqualTo(CandleInterval.ONE_SECOND);
        assertThat(CandleInterval.fromLabel("5s")).isEqualTo(CandleInterval.FIVE_SECONDS);
        assertThat(CandleInterval.fromLabel("1m")).isEqualTo(CandleInterval.ONE_MINUTE);
        assertThat(CandleInterval.fromLabel("15m")).isEqualTo(CandleInterval.FIFTEEN_MIN);
        assertThat(CandleInterval.fromLabel("1h")).isEqualTo(CandleInterval.ONE_HOUR);
    }

    @Test
    void fromLabel_unknownLabel_throwsIllegalArgumentWithMessage() {
        assertThatThrownBy(() -> CandleInterval.fromLabel("99x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown interval: 99x");
    }

    @Test
    void fromLabel_emptyString_throwsIllegalArgument() {
        assertThatThrownBy(() -> CandleInterval.fromLabel(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLabel_null_throwsException() {
        assertThatThrownBy(() -> CandleInterval.fromLabel(null))
            .isInstanceOf(Exception.class);
    }

    @Test
    void fromLabel_caseSensitive_uppercaseThrows() {
        assertThatThrownBy(() -> CandleInterval.fromLabel("1M"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandleInterval.fromLabel("1H"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CandleInterval.fromLabel("1S"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromLabel_whitespace_throwsIllegalArgument() {
        assertThatThrownBy(() -> CandleInterval.fromLabel(" 1m"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── toPgInterval ─────────────────────────────────────────────────────────

    @Test
    void toPgInterval_returnsCorrectSqlLiterals() {
        assertThat(CandleInterval.ONE_SECOND.toPgInterval()).isEqualTo("1 second");
        assertThat(CandleInterval.FIVE_SECONDS.toPgInterval()).isEqualTo("5 seconds");
        assertThat(CandleInterval.ONE_MINUTE.toPgInterval()).isEqualTo("1 minute");
        assertThat(CandleInterval.FIFTEEN_MIN.toPgInterval()).isEqualTo("15 minutes");
        assertThat(CandleInterval.ONE_HOUR.toPgInterval()).isEqualTo("1 hour");
    }

    @Test
    void toPgInterval_noNullValues() {
        for (CandleInterval interval : CandleInterval.values()) {
            assertThat(interval.toPgInterval()).isNotNull().isNotBlank();
        }
    }

    // ── getDurationSeconds ────────────────────────────────────────────────────

    @Test
    void getDurationSeconds_returnsCorrectValues() {
        assertThat(CandleInterval.ONE_SECOND.getDurationSeconds()).isEqualTo(1L);
        assertThat(CandleInterval.FIVE_SECONDS.getDurationSeconds()).isEqualTo(5L);
        assertThat(CandleInterval.ONE_MINUTE.getDurationSeconds()).isEqualTo(60L);
        assertThat(CandleInterval.FIFTEEN_MIN.getDurationSeconds()).isEqualTo(900L);
        assertThat(CandleInterval.ONE_HOUR.getDurationSeconds()).isEqualTo(3600L);
    }

    @Test
    void getDurationSeconds_allPositive() {
        for (CandleInterval interval : CandleInterval.values()) {
            assertThat(interval.getDurationSeconds()).isPositive();
        }
    }

    // ── getLabel / toString ───────────────────────────────────────────────────

    @Test
    void getLabel_returnsExpectedLabels() {
        assertThat(CandleInterval.ONE_SECOND.getLabel()).isEqualTo("1s");
        assertThat(CandleInterval.FIVE_SECONDS.getLabel()).isEqualTo("5s");
        assertThat(CandleInterval.ONE_MINUTE.getLabel()).isEqualTo("1m");
        assertThat(CandleInterval.FIFTEEN_MIN.getLabel()).isEqualTo("15m");
        assertThat(CandleInterval.ONE_HOUR.getLabel()).isEqualTo("1h");
    }

    @Test
    void toString_returnsLabel() {
        assertThat(CandleInterval.ONE_MINUTE.toString()).isEqualTo("1m");
        assertThat(CandleInterval.ONE_HOUR.toString()).isEqualTo("1h");
    }

    @Test
    void getLabel_matchesFromLabel_roundTrip() {
        for (CandleInterval interval : CandleInterval.values()) {
            assertThat(CandleInterval.fromLabel(interval.getLabel())).isEqualTo(interval);
        }
    }

    // ── Enum completeness ─────────────────────────────────────────────────────

    @Test
    void values_exactlyFiveIntervals() {
        assertThat(CandleInterval.values()).hasSize(5);
    }
}

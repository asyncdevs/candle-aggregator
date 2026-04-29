package com.candle.api.service;

import com.candle.api.repository.TickRepository;
import com.candle.common.config.CandleInterval;
import com.candle.common.model.Candle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandleQueryServiceTest {

    @Mock
    private TickRepository tickRepository;

    @InjectMocks
    private CandleQueryService service;

    // ── Delegation to repository ─────────────────────────────────────────────

    @Test
    void getCandles_validInterval_delegatesToRepository() {
        var expected = List.of(sampleCandle(1620000000L));
        when(tickRepository.queryCandles("BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000600L))
            .thenReturn(expected);

        var result = service.getCandles("BTC-USD", "1m", 1620000000L, 1620000600L);

        assertThat(result).isEqualTo(expected);
        verify(tickRepository).queryCandles("BTC-USD", CandleInterval.ONE_MINUTE, 1620000000L, 1620000600L);
    }

    @Test
    void getCandles_passesCorrectSymbolToRepository() {
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong())).thenReturn(List.of());

        service.getCandles("ETH-USD", "1m", 0L, 60L);

        verify(tickRepository).queryCandles(eq("ETH-USD"), any(), anyLong(), anyLong());
    }

    @Test
    void getCandles_passesCorrectTimeBoundsToRepository() {
        long from = 1620000000L;
        long to   = 1620003600L;
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong())).thenReturn(List.of());

        service.getCandles("BTC-USD", "1h", from, to);

        verify(tickRepository).queryCandles("BTC-USD", CandleInterval.ONE_HOUR, from, to);
    }

    @Test
    void getCandles_returnsAllCandlesFromRepository() {
        var candles = List.of(
            sampleCandle(1620000000L),
            sampleCandle(1620000060L),
            sampleCandle(1620000120L)
        );
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong())).thenReturn(candles);

        var result = service.getCandles("BTC-USD", "1m", 1620000000L, 1620000180L);

        assertThat(result).hasSize(3).isEqualTo(candles);
    }

    // ── All intervals resolve correctly ──────────────────────────────────────

    @Test
    void getCandles_allSupportedIntervals_resolveToCorrectEnum() {
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong())).thenReturn(List.of());

        service.getCandles("BTC-USD", "1s",  0L, 1L);
        service.getCandles("BTC-USD", "5s",  0L, 5L);
        service.getCandles("BTC-USD", "1m",  0L, 60L);
        service.getCandles("BTC-USD", "15m", 0L, 900L);
        service.getCandles("BTC-USD", "1h",  0L, 3600L);

        verify(tickRepository).queryCandles(any(), eq(CandleInterval.ONE_SECOND),   anyLong(), anyLong());
        verify(tickRepository).queryCandles(any(), eq(CandleInterval.FIVE_SECONDS), anyLong(), anyLong());
        verify(tickRepository).queryCandles(any(), eq(CandleInterval.ONE_MINUTE),   anyLong(), anyLong());
        verify(tickRepository).queryCandles(any(), eq(CandleInterval.FIFTEEN_MIN),  anyLong(), anyLong());
        verify(tickRepository).queryCandles(any(), eq(CandleInterval.ONE_HOUR),     anyLong(), anyLong());
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    void getCandles_invalidInterval_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getCandles("BTC-USD", "99x", 0L, 1000L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown interval: 99x");
    }

    @Test
    void getCandles_invalidInterval_repositoryNeverCalled() {
        try {
            service.getCandles("BTC-USD", "INVALID", 0L, 1000L);
        } catch (IllegalArgumentException ignored) {}

        verifyNoInteractions(tickRepository);
    }

    @Test
    void getCandles_emptyInterval_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getCandles("BTC-USD", "", 0L, 60L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Empty result ─────────────────────────────────────────────────────────

    @Test
    void getCandles_noDataInRange_returnsEmptyList() {
        when(tickRepository.queryCandles(any(), any(), anyLong(), anyLong())).thenReturn(List.of());

        var result = service.getCandles("ETH-USD", "5s", 1620000000L, 1620000005L);

        assertThat(result).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Candle sampleCandle(long time) {
        return new Candle(time, "BTC-USD", "1m", 29500.0, 29600.0, 29400.0, 29550.0, 10L);
    }
}

package com.candle.api.consumer;

import com.candle.api.repository.TickRepository;
import com.candle.common.model.BidAskEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidAskEventConsumerTest {

    @Mock
    private TickRepository tickRepository;

    @InjectMocks
    private BidAskEventConsumer consumer;

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void consume_callsInsertTick_once() {
        var event = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);

        consumer.consume(event);

        verify(tickRepository, times(1)).insertTick(event);
    }

    @Test
    void consume_passesExactEventToRepository() {
        var event = new BidAskEvent("ETH-USD", bd("3490"), bd("3510"), 1620000001000L);

        consumer.consume(event);

        verify(tickRepository).insertTick(event);
        verifyNoMoreInteractions(tickRepository);
    }

    @Test
    void consume_multipleEvents_eachPersistedExactlyOnce() {
        var e1 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        var e2 = new BidAskEvent("ETH-USD", bd("3490"),  bd("3510"),  1620000001000L);
        var e3 = new BidAskEvent("XAU-USD", bd("2295"),  bd("2305"),  1620000002000L);
        var e4 = new BidAskEvent("XAG-USD", bd("28.45"), bd("28.55"), 1620000003000L);

        consumer.consume(e1);
        consumer.consume(e2);
        consumer.consume(e3);
        consumer.consume(e4);

        verify(tickRepository).insertTick(e1);
        verify(tickRepository).insertTick(e2);
        verify(tickRepository).insertTick(e3);
        verify(tickRepository).insertTick(e4);
        verifyNoMoreInteractions(tickRepository);
    }

    // ── Same-symbol idempotency ───────────────────────────────────────────────

    @Test
    void consume_sameSymbolMultipleTimes_allPersisted() {
        var e1 = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        var e2 = new BidAskEvent("BTC-USD", bd("29010"), bd("29110"), 1620000001000L);

        consumer.consume(e1);
        consumer.consume(e2);

        verify(tickRepository, times(2)).insertTick(any());
        verify(tickRepository).insertTick(e1);
        verify(tickRepository).insertTick(e2);
    }

    // ── Error propagation ─────────────────────────────────────────────────────

    @Test
    void consume_repositoryThrows_exceptionPropagates() {
        var event = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        doThrow(new RuntimeException("DB write failed")).when(tickRepository).insertTick(any());

        assertThrows(RuntimeException.class, () -> consumer.consume(event));
    }

    @Test
    void consume_afterRepositoryThrows_noAdditionalRepositoryCalls() {
        var bad  = new BidAskEvent("BTC-USD", bd("29000"), bd("29100"), 1620000000000L);
        doThrow(new RuntimeException("transient error")).when(tickRepository).insertTick(bad);

        try { consumer.consume(bad); } catch (RuntimeException ignored) {}

        verify(tickRepository, times(1)).insertTick(any());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
    }
}

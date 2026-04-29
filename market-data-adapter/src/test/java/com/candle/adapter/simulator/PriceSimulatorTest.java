package com.candle.adapter.simulator;

import com.candle.common.config.Symbols;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class PriceSimulatorTest {

    private PriceSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new PriceSimulator();
    }

    // ── Return shape ──────────────────────────────────────────────────────────

    @Test
    void nextBidAsk_returnsTwoElementArray() {
        BigDecimal[] result = simulator.nextBidAsk(Symbols.BTC_USD);
        assertThat(result).hasSize(2);
    }

    @Test
    void nextBidAsk_allSymbols_returnTwoElementArrays() {
        for (String symbol : Symbols.ALL) {
            assertThat(simulator.nextBidAsk(symbol)).as("array size for " + symbol).hasSize(2);
        }
    }

    // ── Bid < Ask invariant ───────────────────────────────────────────────────

    @Test
    void nextBidAsk_bidIsAlwaysLessThanAsk_allSymbols() {
        for (String symbol : Symbols.ALL) {
            BigDecimal[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba[0]).as("bid < ask for " + symbol)
                .isLessThan(ba[1]);
        }
    }

    @RepeatedTest(50)
    void nextBidAsk_bidAlwaysLessThanAsk_repeated() {
        for (String symbol : Symbols.ALL) {
            BigDecimal[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba[0]).isLessThan(ba[1]);
        }
    }

    // ── Price bounds (±10% of base) ───────────────────────────────────────────

    @RepeatedTest(30)
    void nextBidAsk_btcUsd_staysWithinTenPercentOfBase() {
        BigDecimal[] ba = simulator.nextBidAsk(Symbols.BTC_USD);
        BigDecimal mid = ba[0].add(ba[1]).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        assertThat(mid).isBetween(new BigDecimal("58500.00"), new BigDecimal("71500.00"));
    }

    @RepeatedTest(30)
    void nextBidAsk_ethUsd_staysWithinTenPercentOfBase() {
        BigDecimal[] ba = simulator.nextBidAsk(Symbols.ETH_USD);
        BigDecimal mid = ba[0].add(ba[1]).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        assertThat(mid).isBetween(new BigDecimal("3150.00"), new BigDecimal("3850.00"));
    }

    @RepeatedTest(30)
    void nextBidAsk_xauUsd_staysWithinTenPercentOfBase() {
        BigDecimal[] ba = simulator.nextBidAsk(Symbols.XAU_USD);
        BigDecimal mid = ba[0].add(ba[1]).divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
        assertThat(mid).isBetween(new BigDecimal("2070.00"), new BigDecimal("2530.00"));
    }

    @RepeatedTest(30)
    void nextBidAsk_xagUsd_staysWithinTenPercentOfBase() {
        BigDecimal[] ba = simulator.nextBidAsk(Symbols.XAG_USD);
        BigDecimal mid = ba[0].add(ba[1]).divide(BigDecimal.valueOf(2), 4, java.math.RoundingMode.HALF_UP);
        assertThat(mid).isBetween(new BigDecimal("25.6500"), new BigDecimal("31.3500"));
    }

    // ── Prices are always positive ────────────────────────────────────────────

    @Test
    void nextBidAsk_allSymbols_noNegativePrices() {
        for (String symbol : Symbols.ALL) {
            BigDecimal[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba[0]).as("bid > 0 for " + symbol).isPositive();
            assertThat(ba[1]).as("ask > 0 for " + symbol).isPositive();
        }
    }

    // ── Rounding precision ────────────────────────────────────────────────────

    @Test
    void nextBidAsk_btcUsd_roundsToTwoDecimalPlaces() {
        for (int i = 0; i < 100; i++) {
            BigDecimal[] ba = simulator.nextBidAsk(Symbols.BTC_USD);
            assertThat(ba[0].scale()).isLessThanOrEqualTo(2);
            assertThat(ba[1].scale()).isLessThanOrEqualTo(2);
        }
    }

    @Test
    void nextBidAsk_xagUsd_roundsToFourDecimalPlaces() {
        for (int i = 0; i < 100; i++) {
            BigDecimal[] ba = simulator.nextBidAsk(Symbols.XAG_USD);
            assertThat(ba[0].scale()).isLessThanOrEqualTo(4);
            assertThat(ba[1].scale()).isLessThanOrEqualTo(4);
        }
    }

    @Test
    void nextBidAsk_returnsBigDecimalNotDouble() {
        BigDecimal[] ba = simulator.nextBidAsk(Symbols.BTC_USD);
        assertThat(ba[0]).isInstanceOf(BigDecimal.class);
        assertThat(ba[1]).isInstanceOf(BigDecimal.class);
    }

    // ── Thread safety ─────────────────────────────────────────────────────────

    @Test
    void nextBidAsk_isThreadSafe_noConcurrentModificationExceptions() throws Exception {
        int threads = 8;
        int callsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<Void>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < callsPerThread; i++) {
                    for (String symbol : Symbols.ALL) {
                        BigDecimal[] ba = simulator.nextBidAsk(symbol);
                        assertThat(ba[0]).isLessThan(ba[1]);
                        assertThat(ba[0]).isPositive();
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();
    }

    // ── Unknown symbol fallback ───────────────────────────────────────────────

    @Test
    void nextBidAsk_unknownSymbol_doesNotThrow() {
        BigDecimal[] ba = simulator.nextBidAsk("UNKNOWN-SYMBOL");
        assertThat(ba).hasSize(2);
        assertThat(ba[0]).isPositive();
        assertThat(ba[1]).isPositive();
    }
}

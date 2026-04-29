package com.candle.adapter.simulator;

import com.candle.common.config.Symbols;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

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
        double[] result = simulator.nextBidAsk(Symbols.BTC_USD);
        assertThat(result).hasSize(2);
    }

    @Test
    void nextBidAsk_allSymbols_returnTwoElementArrays() {
        for (String symbol : Symbols.ALL) {
            double[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba).as("array size for " + symbol).hasSize(2);
        }
    }

    // ── Bid < Ask invariant ───────────────────────────────────────────────────

    @Test
    void nextBidAsk_bidIsAlwaysLessThanAsk_allSymbols() {
        for (String symbol : Symbols.ALL) {
            double[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba[0]).as("bid < ask for " + symbol).isLessThan(ba[1]);
        }
    }

    @RepeatedTest(50)
    void nextBidAsk_bidAlwaysLessThanAsk_repeated() {
        for (String symbol : Symbols.ALL) {
            double[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba[0]).isLessThan(ba[1]);
        }
    }

    // ── Price bounds (±10% of base) ───────────────────────────────────────────

    @RepeatedTest(30)
    void nextBidAsk_btcUsd_staysWithinTenPercentOfBase() {
        double[] ba = simulator.nextBidAsk(Symbols.BTC_USD);
        double mid = (ba[0] + ba[1]) / 2.0;
        assertThat(mid).isBetween(65_000.0 * 0.90, 65_000.0 * 1.10);
    }

    @RepeatedTest(30)
    void nextBidAsk_ethUsd_staysWithinTenPercentOfBase() {
        double[] ba = simulator.nextBidAsk(Symbols.ETH_USD);
        double mid = (ba[0] + ba[1]) / 2.0;
        assertThat(mid).isBetween(3_500.0 * 0.90, 3_500.0 * 1.10);
    }

    @RepeatedTest(30)
    void nextBidAsk_xauUsd_staysWithinTenPercentOfBase() {
        double[] ba = simulator.nextBidAsk(Symbols.XAU_USD);
        double mid = (ba[0] + ba[1]) / 2.0;
        assertThat(mid).isBetween(2_300.0 * 0.90, 2_300.0 * 1.10);
    }

    @RepeatedTest(30)
    void nextBidAsk_xagUsd_staysWithinTenPercentOfBase() {
        double[] ba = simulator.nextBidAsk(Symbols.XAG_USD);
        double mid = (ba[0] + ba[1]) / 2.0;
        assertThat(mid).isBetween(28.50 * 0.90, 28.50 * 1.10);
    }

    // ── Prices are always positive ────────────────────────────────────────────

    @Test
    void nextBidAsk_allSymbols_noNegativePrices() {
        for (String symbol : Symbols.ALL) {
            double[] ba = simulator.nextBidAsk(symbol);
            assertThat(ba[0]).as("bid > 0 for " + symbol).isPositive();
            assertThat(ba[1]).as("ask > 0 for " + symbol).isPositive();
        }
    }

    // ── Rounding precision ────────────────────────────────────────────────────

    @Test
    void nextBidAsk_btcUsd_roundsToTwoDecimalPlaces() {
        for (int i = 0; i < 100; i++) {
            double[] ba = simulator.nextBidAsk(Symbols.BTC_USD);
            assertThat(ba[0]).isEqualTo(Math.round(ba[0] * 100) / 100.0);
            assertThat(ba[1]).isEqualTo(Math.round(ba[1] * 100) / 100.0);
        }
    }

    @Test
    void nextBidAsk_ethUsd_roundsToTwoDecimalPlaces() {
        for (int i = 0; i < 100; i++) {
            double[] ba = simulator.nextBidAsk(Symbols.ETH_USD);
            assertThat(ba[0]).isEqualTo(Math.round(ba[0] * 100) / 100.0);
            assertThat(ba[1]).isEqualTo(Math.round(ba[1] * 100) / 100.0);
        }
    }

    @Test
    void nextBidAsk_xauUsd_roundsToTwoDecimalPlaces() {
        for (int i = 0; i < 100; i++) {
            double[] ba = simulator.nextBidAsk(Symbols.XAU_USD);
            assertThat(ba[0]).isEqualTo(Math.round(ba[0] * 100) / 100.0);
            assertThat(ba[1]).isEqualTo(Math.round(ba[1] * 100) / 100.0);
        }
    }

    @Test
    void nextBidAsk_xagUsd_roundsToFourDecimalPlaces() {
        for (int i = 0; i < 100; i++) {
            double[] ba = simulator.nextBidAsk(Symbols.XAG_USD);
            assertThat(ba[0]).isEqualTo(Math.round(ba[0] * 10_000) / 10_000.0);
            assertThat(ba[1]).isEqualTo(Math.round(ba[1] * 10_000) / 10_000.0);
        }
    }

    // ── Spread correctness ────────────────────────────────────────────────────

    @Test
    void nextBidAsk_btcUsd_spreadIsApproximately002Pct() {
        // BTC spread = 0.02% of mid-price
        for (int i = 0; i < 20; i++) {
            double[] ba = simulator.nextBidAsk(Symbols.BTC_USD);
            double mid    = (ba[0] + ba[1]) / 2.0;
            double spread = ba[1] - ba[0];
            double expectedSpread = mid * 0.0002;
            // Allow rounding tolerance of ±1 cent
            assertThat(spread).isBetween(expectedSpread - 0.02, expectedSpread + 0.02);
        }
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
                        double[] ba = simulator.nextBidAsk(symbol);
                        assertThat(ba[0]).isLessThan(ba[1]);
                        assertThat(ba[0]).isPositive();
                    }
                }
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            f.get(); // propagates any assertion errors from worker threads
        }
        executor.shutdown();
    }

    // ── Unknown symbol fallback ───────────────────────────────────────────────

    @Test
    void nextBidAsk_unknownSymbol_doesNotThrow() {
        double[] ba = simulator.nextBidAsk("UNKNOWN-SYMBOL");
        assertThat(ba).hasSize(2);
        assertThat(ba[0]).isPositive();
        assertThat(ba[1]).isPositive();
    }
}

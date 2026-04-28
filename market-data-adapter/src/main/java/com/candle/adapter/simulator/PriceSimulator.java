package com.candle.adapter.simulator;

import com.candle.common.config.Symbols;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates realistic bid/ask prices using a random walk model.
 *
 * Each symbol starts at a realistic base price and walks randomly
 * within a small percentage range per tick — mimicking real market
 * micro-movements without extreme volatility.
 */
@Component
public class PriceSimulator {

    // Realistic base prices per symbol
    private static final Map<String, Double> BASE_PRICES = Map.of(
            Symbols.BTC_USD, 65_000.00,
            Symbols.ETH_USD,  3_500.00,
            Symbols.XAU_USD,  2_300.00,
            Symbols.XAG_USD,     28.50
    );

    // Spread as percentage of mid-price (tight spreads for liquid markets)
    private static final Map<String, Double> SPREAD_PCT = Map.of(
            Symbols.BTC_USD, 0.0002,  // 0.02% spread
            Symbols.ETH_USD, 0.0003,
            Symbols.XAU_USD, 0.0001,
            Symbols.XAG_USD, 0.0005
    );

    // Max random walk per tick as percentage of current price
    private static final double TICK_VOLATILITY = 0.0005; // 0.05%

    // Current mid-prices — walks from here each tick
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>(BASE_PRICES);

    /**
     * Returns the next [bid, ask] for the given symbol.
     * Mutates internal state — each call advances the random walk.
     */
    public double[] nextBidAsk(String symbol) {
        double current = currentPrices.compute(symbol, (s, price) -> {
            if (price == null) price = BASE_PRICES.getOrDefault(s, 100.0);
            // Random walk: move up or down by up to TICK_VOLATILITY %
            double change = price * TICK_VOLATILITY
                    * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
            // Clamp: price should not deviate more than 10% from base
            double base    = BASE_PRICES.getOrDefault(s, price);
            double newPrice = price + change;
            newPrice = Math.max(newPrice, base * 0.90);
            newPrice = Math.min(newPrice, base * 1.10);
            return newPrice;
        });

        double spreadPct = SPREAD_PCT.getOrDefault(symbol, 0.0002);
        double halfSpread = current * spreadPct / 2.0;

        double bid = round(current - halfSpread, symbol);
        double ask = round(current + halfSpread, symbol);

        return new double[]{bid, ask};
    }

    /** Round to appropriate decimal places per instrument type. */
    private double round(double value, String symbol) {
        int decimals = switch (symbol) {
            case Symbols.BTC_USD -> 2;
            case Symbols.ETH_USD -> 2;
            case Symbols.XAU_USD -> 2;
            case Symbols.XAG_USD -> 4;
            default              -> 4;
        };
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}

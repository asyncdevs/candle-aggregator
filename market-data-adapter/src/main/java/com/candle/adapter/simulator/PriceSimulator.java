package com.candle.adapter.simulator;

import com.candle.common.config.Symbols;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates realistic bid/ask prices using a random walk model.
 *
 * Each symbol starts at a realistic base price and walks randomly
 * within a small percentage range per tick — mimicking real market
 * micro-movements without extreme volatility.
 *
 * Internal price state is maintained as double for efficient random-walk
 * arithmetic. Outputs are converted to BigDecimal at the boundary to
 * preserve exact decimal representation for downstream financial calculations.
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
     * Returns the next [bid, ask] for the given symbol as BigDecimal.
     * Mutates internal state — each call advances the random walk.
     */
    public BigDecimal[] nextBidAsk(String symbol) {
        double current = currentPrices.compute(symbol, (s, price) -> {
            if (price == null) price = BASE_PRICES.getOrDefault(s, 100.0);
            double change = price * TICK_VOLATILITY
                    * (ThreadLocalRandom.current().nextDouble() * 2 - 1);
            double base    = BASE_PRICES.getOrDefault(s, price);
            double newPrice = price + change;
            newPrice = Math.max(newPrice, base * 0.90);
            newPrice = Math.min(newPrice, base * 1.10);
            return newPrice;
        });

        double spreadPct = SPREAD_PCT.getOrDefault(symbol, 0.0002);
        double halfSpread = current * spreadPct / 2.0;

        BigDecimal bid = round(current - halfSpread, symbol);
        BigDecimal ask = round(current + halfSpread, symbol);

        return new BigDecimal[]{bid, ask};
    }

    /** Round to appropriate decimal places per instrument type. */
    private BigDecimal round(double value, String symbol) {
        int decimals = switch (symbol) {
            case Symbols.BTC_USD -> 2;
            case Symbols.ETH_USD -> 2;
            case Symbols.XAU_USD -> 2;
            case Symbols.XAG_USD -> 4;
            default              -> 4;
        };
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
    }
}

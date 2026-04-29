package com.candle.common.config;

import java.util.List;

/**
 * Supported trading symbols.
 * Adding a new symbol requires only adding it here.
 */
public final class Symbols {

    private Symbols() {}

    public static final String BTC_USD = "BTC-USD";
    public static final String ETH_USD = "ETH-USD";
    public static final String XAU_USD = "XAU-USD";
    public static final String XAG_USD = "XAG-USD";

    public static final List<String> ALL = List.of(BTC_USD, ETH_USD, XAU_USD, XAG_USD);
}

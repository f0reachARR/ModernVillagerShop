package me.f0reach.vshop.sound;

import java.util.List;

/**
 * Event keys understood by {@link SoundService}. Each constant is a dotted
 * path into the {@code sounds:} section of {@code config.yml} — Bukkit's
 * YAML accessor splits on {@code .}, so the underlying YAML must nest each
 * segment as its own map.
 */
public final class SoundEvents {

    /** Player right-clicked a shop villager and a menu/browse view opens. */
    public static final String UI_OPEN = "ui.open";

    /** SELL transaction committed successfully. */
    public static final String TRADE_SUCCESS_SELL = "trade.success.sell";

    /** BUY (order-slot delivery) transaction committed successfully. */
    public static final String TRADE_SUCCESS_BUY = "trade.success.buy";

    /** Trade rejected — out of stock, not enough money/items, limit reached, ... */
    public static final String TRADE_FAILURE = "trade.failure";

    /**
     * Every event {@link SoundService} tries to parse on load. Adding a new
     * event means adding a constant above and its entry here — extra sections
     * in the YAML that aren't listed are ignored.
     */
    static final List<String> ALL = List.of(
            UI_OPEN,
            TRADE_SUCCESS_SELL,
            TRADE_SUCCESS_BUY,
            TRADE_FAILURE
    );

    private SoundEvents() {}
}

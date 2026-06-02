package me.f0reach.vshop.api.price;

/**
 * Pipelined SPI for dynamic admin-shop pricing. Providers are evaluated in
 * ascending {@link #order()} order; each receives the previous provider's
 * {@link PriceResult} so it can pass through, overwrite, or differentially
 * adjust the price. Player shops do not invoke providers — their static price
 * is final.
 *
 * <p>See spec §12.3. Implementations must NOT block: any I/O belongs off the
 * provider call path (cache lookups, async pre-warm, etc.).</p>
 */
public interface PriceProvider {

    /** Stable identifier — appears in {@code shop_transactions.resolved_by}. */
    String id();

    /** Lower runs first. Built-in static price is treated as order 0. */
    default int order() { return 100; }

    /** Resolve the price for this stage of the pipeline. */
    PriceResult apply(PriceContext context, PriceResult previous);
}

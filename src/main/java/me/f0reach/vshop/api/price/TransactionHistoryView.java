package me.f0reach.vshop.api.price;

import me.f0reach.vshop.model.TradeSide;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read-only accessor over a single {@code (shop, slot)}'s transaction history.
 * Passed to {@link PriceProvider#apply} via {@link PriceContext#history()} so
 * providers can price against cumulative or windowed activity.
 *
 * Implementations may hit the database on each call; per spec §12.3.3 a
 * provider must not block on I/O, so callers should cache the result inside
 * the {@link PriceResult#ttl()} window rather than re-querying every paint.
 *
 * Time bounds are exclusive-from / inclusive-to and accept {@code null} for
 * unbounded. {@link TradeSide} {@code null} sums both sides.
 */
public interface TransactionHistoryView {

    /** Total quantity of items traded over the range. */
    long totalAmount(@Nullable TradeSide side, @Nullable Instant from, @Nullable Instant to);

    /** Sum of {@code unit_price * amount} over the range. */
    BigDecimal totalValue(@Nullable TradeSide side, @Nullable Instant from, @Nullable Instant to);

    /** Number of trade rows in the range. */
    long count(@Nullable TradeSide side, @Nullable Instant from, @Nullable Instant to);
}

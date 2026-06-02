package me.f0reach.vshop.api.price;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Output of one provider stage. A null {@code price} means "pass through" —
 * the previous stage's result remains in effect. {@code reason} is appended
 * to chest-UI lore as the per-row explanation.
 */
public record PriceResult(
        @Nullable BigDecimal price,
        @Nullable Component reason,
        Duration ttl
) {
    public static PriceResult passthrough() { return new PriceResult(null, null, Duration.ZERO); }
    public static PriceResult of(BigDecimal price) { return new PriceResult(price, null, Duration.ZERO); }
}

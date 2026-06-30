package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.api.price.PriceContext;
import me.f0reach.vshop.api.price.PriceRegistry;
import me.f0reach.vshop.api.price.PriceResult;
import me.f0reach.vshop.api.price.TransactionHistoryView;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.StorageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges {@link PriceRegistry} with the trade and UI paths. Centralises the
 * three pieces of spec §12.3 that the rest of the plugin shouldn't have to
 * know about:
 *
 *   1. Player shops and the {@code economy.priceProvider.enabled = false}
 *      kill switch bypass providers entirely and return the static slot price.
 *   2. The {@link PriceResult#ttl()} hint is honored so chest-UI repaints
 *      don't thrash providers every tick.
 *   3. The {@code resolvedBy} chain produced by {@link PriceRegistry#resolve}
 *      is plumbed back out so the trade record can be written.
 */
public final class PriceResolver {

    private final PriceRegistry registry;
    private final PluginConfig config;
    private final StorageManager storage;
    private final Map<CacheKey, CachedResolution> cache = new ConcurrentHashMap<>();

    public PriceResolver(PriceRegistry registry, PluginConfig config, StorageManager storage) {
        this.registry = registry;
        this.config = config;
        this.storage = storage;
    }

    /**
     * Resolves the live price for a slot. The viewer is included in the cache
     * key (when non-null) because providers may price per-player.
     */
    public Resolution resolve(Shop shop, ShopSlot slot, TradeSide side,
                              @Nullable OfflinePlayer viewer, int intendedAmount) {
        BigDecimal basePrice = sideBasePrice(slot, side);
        if (!config.economy().priceProviderEnabled() || shop.isPlayerShop()) {
            return new Resolution(basePrice, basePrice, null, null);
        }

        UUID viewerKey = viewer == null ? null : viewer.getUniqueId();
        CacheKey key = new CacheKey(shop.id(), slot.id(), side, viewerKey);
        CachedResolution cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.resolution;
        }

        TransactionHistoryView history = new DbTransactionHistoryView(storage, shop.id(), slot.id());
        PriceContext ctx = PriceContext.of(shop, slot, side, viewer, intendedAmount, basePrice, history);
        PriceResult result;
        try {
            result = registry.resolve(ctx);
        } catch (RuntimeException ex) {
            // Defensive — PriceRegistry already swallows provider exceptions,
            // but a registry-side bug shouldn't kill the trade path.
            return new Resolution(basePrice, basePrice, null, null);
        }

        BigDecimal finalPrice = result.price() != null ? result.price() : basePrice;
        String resolvedBy = (String) ctx.attrs().get("resolvedBy");
        Resolution resolution = new Resolution(basePrice, finalPrice, result.reason(),
                resolvedBy != null && !resolvedBy.isEmpty() ? resolvedBy : null);

        Duration ttl = result.ttl();
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            cache.put(key, new CachedResolution(now.plus(ttl), resolution));
        }
        return resolution;
    }

    /**
     * Returns true when the live-resolved {@code current} price has drifted
     * from the snapshot beyond {@code economy.priceDriftTolerance}.
     */
    public boolean isDriftBeyondTolerance(BigDecimal snapshot, BigDecimal current) {
        return isDriftBeyondTolerance(snapshot, current, config.economy().priceDriftTolerance());
    }

    /**
     * Pure helper exposed for unit-testing. Both prices are compared as
     * relative drift against the larger of the two — that avoids the
     * divide-by-zero edge case when either price is zero. A null/non-positive
     * tolerance means "any drift is fine".
     */
    public static boolean isDriftBeyondTolerance(BigDecimal snapshot, BigDecimal current, BigDecimal tolerance) {
        if (snapshot == null || current == null) return false;
        if (tolerance == null || tolerance.signum() <= 0) return false;
        BigDecimal diff = snapshot.subtract(current).abs();
        if (diff.signum() == 0) return false;
        BigDecimal denom = snapshot.abs().max(current.abs());
        if (denom.signum() == 0) return false;
        BigDecimal ratio = diff.divide(denom, 8, java.math.RoundingMode.HALF_UP);
        return ratio.compareTo(tolerance) > 0;
    }

    public void invalidate(UUID shopId) {
        cache.keySet().removeIf(k -> k.shopId.equals(shopId));
    }

    public void invalidateAll() {
        cache.clear();
    }

    private static BigDecimal sideBasePrice(ShopSlot slot, TradeSide side) {
        if (side == TradeSide.BUY) {
            return slot.buyUnitPrice() != null ? slot.buyUnitPrice() : slot.unitPrice();
        }
        return slot.unitPrice();
    }

    /**
     * The result of one resolve() call. {@code resolvedBy} is non-null only
     * when at least one provider mutated the price.
     */
    public record Resolution(
            BigDecimal basePrice,
            BigDecimal finalPrice,
            @Nullable Component reason,
            @Nullable String resolvedBy
    ) {}

    private record CacheKey(UUID shopId, UUID slotId, TradeSide side, @Nullable UUID viewer) {}

    private record CachedResolution(Instant expiresAt, Resolution resolution) {}
}

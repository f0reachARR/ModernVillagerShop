package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.model.ShopEntityKind;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mirror of {@code shop_appearance}, so backend resolution on every
 * interaction is a map lookup rather than a query. Mirrors {@link
 * me.f0reach.vshop.shop.ShopRegistry}'s role for shops.
 *
 * <p>A missing entry is the common case and means "plain Villager".
 */
public final class ShopAppearanceRegistry {

    private final Map<UUID, ShopAppearance> byShop = new ConcurrentHashMap<>();

    public void loadAll(Collection<ShopAppearance> appearances) {
        byShop.clear();
        for (ShopAppearance a : appearances) byShop.put(a.shopId(), a);
    }

    public Optional<ShopAppearance> find(UUID shopId) {
        return Optional.ofNullable(byShop.get(shopId));
    }

    /** The appearance for this shop, or the implicit Villager default. */
    public ShopAppearance getOrDefault(UUID shopId) {
        ShopAppearance a = byShop.get(shopId);
        return a != null ? a : ShopAppearance.defaultFor(shopId);
    }

    public ShopEntityKind backendOf(UUID shopId) {
        ShopAppearance a = byShop.get(shopId);
        return a == null ? ShopEntityKind.VILLAGER : a.backend();
    }

    public void put(ShopAppearance appearance) {
        byShop.put(appearance.shopId(), appearance);
    }

    public void remove(UUID shopId) {
        byShop.remove(shopId);
    }

    public Collection<ShopAppearance> all() {
        return new java.util.ArrayList<>(byShop.values());
    }

    public int countByBackend(ShopEntityKind kind) {
        int n = 0;
        for (ShopAppearance a : byShop.values()) {
            if (a.backend() == kind) n++;
        }
        return n;
    }
}

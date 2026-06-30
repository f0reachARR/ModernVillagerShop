package me.f0reach.vshop.api;

import me.f0reach.vshop.api.price.PriceRegistry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.storage.StorageManager;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public facade for third-party integrations. Resolved via
 * {@code Bukkit.getServicesManager().getRegistration(ModernVillagerShopAPI.class)}
 * once the plugin enables.
 */
public final class ModernVillagerShopAPI {

    private final ShopRegistry shopRegistry;
    private final StorageManager storage;
    private final PriceRegistry priceRegistry;

    public ModernVillagerShopAPI(ShopRegistry shopRegistry, StorageManager storage, PriceRegistry priceRegistry) {
        this.shopRegistry = shopRegistry;
        this.storage = storage;
        this.priceRegistry = priceRegistry;
    }

    public PriceRegistry priceRegistry() { return priceRegistry; }

    public Optional<Shop> shop(UUID id) { return shopRegistry.byId(id); }

    public Collection<Shop> allShops() { return shopRegistry.all(); }

    public List<TradeRecord> shopHistory(UUID shopId, int limit, int offset) throws SQLException {
        return storage.transactions().findByShop(shopId, limit, offset);
    }

    public List<TradeRecord> playerHistory(UUID playerUuid, int limit, int offset) throws SQLException {
        return storage.transactions().findByPlayer(playerUuid, limit, offset);
    }

    public me.f0reach.vshop.storage.repo.ShopTransactionRepository.AggregateStats statsFor(UUID shopId) throws SQLException {
        return storage.transactions().aggregate(shopId);
    }

    /**
     * Find a shop by case-insensitive substring of its display name.
     * Returns an empty list when no shop matches. The lookup is O(N) over the
     * in-memory registry — callers iterating over many queries should cache.
     */
    public List<Shop> findShopsByName(String query) {
        if (query == null || query.isBlank()) return List.of();
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        List<Shop> out = new java.util.ArrayList<>();
        for (Shop s : shopRegistry.all()) {
            if (s.name() != null && s.name().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                out.add(s);
            }
        }
        return out;
    }

    /** All shops owned (as PRIMARY) by the given player. */
    public List<Shop> findShopsByOwner(UUID ownerUuid) {
        List<Shop> out = new java.util.ArrayList<>();
        for (Shop s : shopRegistry.all()) {
            if (s.isPlayerShop() && ownerUuid.equals(s.ownerUuid())) out.add(s);
        }
        return out;
    }

    /**
     * Find a shop by a prefix of its UUID. Useful for third-party tools that
     * receive a truncated id from chat/log output.
     */
    public Optional<Shop> findShopByIdPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return Optional.empty();
        String needle = prefix.toLowerCase(java.util.Locale.ROOT);
        for (Shop s : shopRegistry.all()) {
            if (s.id().toString().toLowerCase(java.util.Locale.ROOT).startsWith(needle)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}

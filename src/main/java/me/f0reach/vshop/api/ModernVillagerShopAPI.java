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
}

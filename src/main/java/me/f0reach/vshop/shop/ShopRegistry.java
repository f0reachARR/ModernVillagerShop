package me.f0reach.vshop.shop;

import me.f0reach.vshop.model.Shop;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of all loaded shops, keyed by shop id and villager-entity id
 * for fast event-handler lookups.
 */
public final class ShopRegistry {

    private final Map<UUID, Shop> byId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> villagerToShop = new ConcurrentHashMap<>();

    public void loadAll(Collection<Shop> shops) {
        byId.clear();
        villagerToShop.clear();
        for (Shop shop : shops) put(shop);
    }

    public void put(Shop shop) {
        byId.put(shop.id(), shop);
        UUID v = shop.villagerEntityId();
        if (v != null) {
            // Remove any old villager mapping that may have been associated with
            // this shop, then index the current one.
            villagerToShop.entrySet().removeIf(e -> e.getValue().equals(shop.id()));
            villagerToShop.put(v, shop.id());
        }
    }

    public void remove(UUID shopId) {
        Shop removed = byId.remove(shopId);
        if (removed != null && removed.villagerEntityId() != null) {
            villagerToShop.remove(removed.villagerEntityId(), shopId);
        }
        villagerToShop.entrySet().removeIf(e -> e.getValue().equals(shopId));
    }

    public Optional<Shop> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<Shop> byVillager(UUID villagerEntityId) {
        UUID shopId = villagerToShop.get(villagerEntityId);
        return shopId == null ? Optional.empty() : byId(shopId);
    }

    public Collection<Shop> all() {
        return new java.util.ArrayList<>(byId.values());
    }

    public boolean isShopVillager(UUID entityId) {
        return villagerToShop.containsKey(entityId);
    }

    public int countByOwner(UUID ownerUuid) {
        int n = 0;
        for (Shop shop : byId.values()) {
            if (shop.isPlayerShop() && ownerUuid.equals(shop.ownerUuid())) n++;
        }
        return n;
    }

    public Map<UUID, Shop> map() {
        return new HashMap<>(byId);
    }
}

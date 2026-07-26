package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Single entry point for manipulating a shop's in-world representation.
 * Resolves which {@link ShopEntityBackend} owns a given shop and forwards to it.
 *
 * <p>Only {@link VillagerBackend} exists today, so every shop resolves to it.
 * When a second backend lands, the resolution rule changes here and callers
 * stay untouched.
 */
public final class ShopEntityService implements ShopEntityBackend {

    private final VillagerBackend villagers;

    public ShopEntityService(VillagerBackend villagers) {
        this.villagers = villagers;
    }

    /**
     * The villager backend. Exposed for the genuinely villager-specific bits
     * (the PDC key, entity lookup) that have no meaning for other backends.
     */
    public VillagerBackend villagers() {
        return villagers;
    }

    private ShopEntityBackend backendFor(Shop shop) {
        return villagers;
    }

    @Override
    public UUID spawn(Shop shop, Location at) {
        return backendFor(shop).spawn(shop, at);
    }

    @Override
    public void refresh(Shop shop) {
        backendFor(shop).refresh(shop);
    }

    @Override
    public void refreshDisplayName(Shop shop) {
        backendFor(shop).refreshDisplayName(shop);
    }

    @Override
    public void remove(Shop shop) {
        backendFor(shop).remove(shop);
    }
}

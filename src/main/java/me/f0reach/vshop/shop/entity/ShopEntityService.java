package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopEntityKind;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Single entry point for manipulating a shop's in-world representation.
 * Resolves which {@link ShopEntityBackend} owns a given shop and forwards to it.
 *
 * <p>Resolution is driven by {@link ShopAppearanceRegistry}. When a shop asks
 * for a backend that is not available — FancyNpcs not installed, or disabled in
 * config — it silently falls back to the Villager backend: a missing cosmetic
 * plugin must never take a shop offline.
 */
public final class ShopEntityService implements ShopEntityBackend {

    private final VillagerBackend villagers;
    private final ShopAppearanceRegistry appearances;
    private final ShopEntityBackend npc;

    public ShopEntityService(VillagerBackend villagers, ShopAppearanceRegistry appearances,
                             ShopEntityBackend npc) {
        this.villagers = villagers;
        this.appearances = appearances;
        this.npc = npc;
    }

    /**
     * The villager backend. Exposed for the genuinely villager-specific bits
     * (the PDC key, entity lookup) that have no meaning for other backends.
     */
    public VillagerBackend villagers() {
        return villagers;
    }

    public ShopAppearanceRegistry appearances() {
        return appearances;
    }

    /** True when the shop currently renders as something other than a Villager. */
    public boolean isNpcBacked(Shop shop) {
        return backendFor(shop) == npc;
    }

    private ShopEntityBackend backendFor(Shop shop) {
        if (npc != null && appearances.backendOf(shop.id()) == ShopEntityKind.FANCY_NPC) {
            return npc;
        }
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

    /**
     * Despawns the shop and forgets its appearance. Separate from {@link #remove}
     * because a shop being deleted must also drop out of the appearance registry,
     * whereas a temporary despawn must not.
     */
    public void onShopDeleted(Shop shop) {
        remove(shop);
        appearances.remove(shop.id());
    }

    /**
     * Rebuilds the representation from the current appearance, switching backend
     * if it changed. Returns the Bukkit entity id to persist on the shop — null
     * for backends without one — so the caller can write it back.
     *
     * <p>Removes through both backends because the shop may be mid-switch and
     * the old representation is not the one {@link #backendFor} now resolves to.
     */
    public UUID respawn(Shop shop) {
        Location at = shop.location().toBukkit();
        if (at == null) return shop.villagerEntityId();
        villagers.remove(shop);
        if (npc != null) npc.remove(shop);
        return spawn(shop, at);
    }
}

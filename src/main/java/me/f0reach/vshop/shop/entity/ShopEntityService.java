package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
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
        return npc != null && backendFor(shop) == npc;
    }

    private ShopEntityBackend backendFor(Shop shop) {
        return backendFor(shop.id());
    }

    private ShopEntityBackend backendFor(UUID shopId) {
        if (npc != null && appearances.backendOf(shopId) == ShopEntityKind.FANCY_NPC) {
            return npc;
        }
        return villagers;
    }

    @Override
    public void prepare(ShopAppearance appearance) {
        backendFor(appearance.shopId()).prepare(appearance);
    }

    /** Off-thread warm-up for this shop's current appearance. See {@link ShopEntityBackend#prepare}. */
    public void prepare(Shop shop) {
        prepare(appearances.getOrDefault(shop.id()));
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
     * Removes a villager still standing for a shop that now renders as an NPC and
     * clears the stale id, returning true when the shop record changed.
     *
     * <p>Only acts when the entity is actually reachable. If its chunk is not
     * loaded the id has to stay put — clearing it would orphan the villager with
     * nothing left pointing at it. {@code ShopVillagerListener} finishes the job
     * when the chunk does load.
     *
     * <p>Exists because a crash between despawning a villager and persisting that
     * leaves the two out of step, and spawn chunks are already loaded before
     * plugins enable, so the chunk-load pass alone never sees them.
     */
    public boolean discardStrayVillager(Shop shop) {
        if (!isNpcBacked(shop) || shop.villagerEntityId() == null) return false;
        var stray = villagers.findEntity(shop);
        if (stray == null) return false;
        stray.remove();
        shop.setVillagerEntityId(null);
        return true;
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

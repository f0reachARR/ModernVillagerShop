package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Backs a shop with a real, AI-disabled, invulnerable {@link Villager} and keeps
 * its appearance (custom name, profession, flags) in sync with the shop record.
 */
public final class VillagerBackend implements ShopEntityBackend {

    public static final String VILLAGER_PDC_KEY = "shop_id";

    private final ShopDisplayName displayName;
    private final NamespacedKey villagerKey;

    public VillagerBackend(Plugin plugin, ShopDisplayName displayName) {
        this.displayName = displayName;
        this.villagerKey = new NamespacedKey(plugin, VILLAGER_PDC_KEY);
    }

    /** PDC key stamped on every shop villager, for fast event-side identification. */
    public NamespacedKey villagerKey() {
        return villagerKey;
    }

    @Override
    public UUID spawn(Shop shop, Location at) {
        Villager villager = at.getWorld().spawn(at, Villager.class, v -> applyAttributes(v, shop));
        return villager.getUniqueId();
    }

    @Override
    public void refresh(Shop shop) {
        Villager v = findEntity(shop);
        if (v == null) return;
        applyAttributes(v, shop);
    }

    @Override
    public void refreshDisplayName(Shop shop) {
        // Best-effort: if the villager isn't currently loaded, the next chunk-load
        // will pick up the change via spawn().
        Villager v = findEntity(shop);
        if (v == null) return;
        v.customName(displayName.component(shop));
        v.setCustomNameVisible(true);
    }

    @Override
    public void remove(Shop shop) {
        Villager v = findEntity(shop);
        if (v != null) v.remove();
    }

    private void applyAttributes(Villager v, Shop shop) {
        v.setAI(false);
        v.setInvulnerable(true);
        v.setRemoveWhenFarAway(false);
        v.setPersistent(true);
        v.setCollidable(false);
        v.setCanPickupItems(false);
        var maxHealth = v.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
            v.setHealth(maxHealth.getValue());
        }

        if (shop.profession() != null) {
            v.setProfession(shop.profession());
        }
        // Mark this villager as belonging to a shop for fast event-side lookup.
        v.getPersistentDataContainer().set(villagerKey, PersistentDataType.STRING, shop.id().toString());

        v.customName(displayName.component(shop));
        v.setCustomNameVisible(true);
    }

    /**
     * Returns the shop villager matching the entity id persisted on the shop
     * record, or null if it is not currently loaded.
     */
    public Villager findEntity(Shop shop) {
        if (shop.villagerEntityId() == null) return null;
        if (shop.location().toBukkit() == null) return null;
        var entity = Bukkit.getEntity(shop.villagerEntityId());
        if (entity instanceof Villager v) return v;
        return null;
    }
}

package me.f0reach.vshop.shop.listener;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.ShopVillagerManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Keeps shop villagers safe and stationary:
 *  - cancels damage / portal teleport / zombie transformation
 *  - blocks the vanilla trade UI (we use our own)
 *  - re-spawns the villager on chunk load if its entity has vanished
 */
public final class ShopVillagerListener implements Listener {

    private final ShopRegistry registry;
    private final ShopService shops;
    private final NamespacedKey villagerKey;
    private final PluginConfig config;

    public ShopVillagerListener(ShopRegistry registry, ShopService shops, ShopVillagerManager villagers,
                                PluginConfig config) {
        this.registry = registry;
        this.shops = shops;
        this.villagerKey = villagers.villagerKey();
        this.config = config;
    }

    private boolean isShopVillager(Entity entity) {
        if (!(entity instanceof Villager)) return false;
        if (registry.isShopVillager(entity.getUniqueId())) return true;
        // Fallback: villager was loaded before the registry caught up (e.g. async chunk).
        return entity.getPersistentDataContainer().has(villagerKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isShopVillager(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        if (isShopVillager(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        // The villager itself may be teleported by other plugins/world quirks.
        // Cancel so it cannot leave its anchored location.
        if (isShopVillager(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        // Block zombie-villager conversion etc.
        if (isShopVillager(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!isShopVillager(entity)) return;
        // Suppress the vanilla trade UI; the actual shop UI is opened by the
        // separate ShopOpenListener after permission checks.
        event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Iterate over shops whose anchor is in this chunk and re-spawn any
        // whose villager is missing. Cheap because the registry is in-memory.
        UUID world = event.getWorld().getUID();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        for (var shop : registry.all()) {
            if (!world.equals(shop.location().worldId())) continue;
            int sx = (int) Math.floor(shop.location().x()) >> 4;
            int sz = (int) Math.floor(shop.location().z()) >> 4;
            if (sx != cx || sz != cz) continue;

            UUID villagerId = shop.villagerEntityId();
            if (villagerId == null) continue;
            Entity entity = event.getWorld().getEntities().stream()
                    .filter(e -> e.getUniqueId().equals(villagerId))
                    .findFirst().orElse(null);
            if (entity == null) {
                var at = shop.location().toBukkit();
                if (at == null) continue;
                UUID newId = shops.villagers().spawn(shop, at, config);
                shop.setVillagerEntityId(newId);
                try {
                    shops.update(shop);
                } catch (java.sql.SQLException ex) {
                    // Already logged in the service layer; we don't need to abort the chunk-load.
                }
            }
        }
    }
}

package me.f0reach.vshop.shop.listener;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.ShopInteractionRouter;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.VillagerTeleportGuard;
import me.f0reach.vshop.shop.entity.VillagerBackend;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
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
    private final ShopInteractionRouter router;
    private final VillagerTeleportGuard teleportGuard;

    public ShopVillagerListener(ShopRegistry registry, ShopService shops, VillagerBackend villagers,
                                ShopInteractionRouter router, VillagerTeleportGuard teleportGuard) {
        this.registry = registry;
        this.shops = shops;
        this.villagerKey = villagers.villagerKey();
        this.router = router;
        this.teleportGuard = teleportGuard;
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
        // Cancel so it cannot leave its anchored location — except when a
        // trusted internal caller (e.g. VillagerLookListener re-orienting the
        // villager) has whitelisted this specific teleport.
        Entity entity = event.getEntity();
        if (!isShopVillager(entity)) return;
        if (teleportGuard.isSuppressed(entity.getUniqueId())) return;
        event.setCancelled(true);
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
        // Suppress the vanilla trade UI — we open our own browse view instead.
        event.setCancelled(true);
        Shop shop = registry.byVillager(entity.getUniqueId()).orElse(null);
        if (shop == null) return;
        if (!(event.getPlayer() instanceof Player viewer)) return;
        router.onRightClick(viewer, shop);
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

            // NPC-backed shops are packet-based: spawned once at boot, unaffected
            // by chunk loading. They should own no villager at all, so a leftover
            // id means a crash landed between despawning one and persisting that
            // — clean it up now that the chunk is finally loaded.
            if (shops.entities().isNpcBacked(shop)) {
                if (villagerId != null) discardStrayVillager(event, shop, villagerId);
                continue;
            }

            if (villagerId == null) continue;
            Entity entity = findInWorld(event, villagerId);
            if (entity == null) {
                var at = shop.location().toBukkit();
                if (at == null) continue;
                UUID newId = shops.entities().spawn(shop, at);
                shop.setVillagerEntityId(newId);
                persist(shop);
            }
        }
    }

    private void discardStrayVillager(ChunkLoadEvent event, Shop shop, UUID villagerId) {
        Entity stray = findInWorld(event, villagerId);
        if (stray != null) stray.remove();
        shop.setVillagerEntityId(null);
        persist(shop);
    }

    private static Entity findInWorld(ChunkLoadEvent event, UUID entityId) {
        return event.getWorld().getEntities().stream()
                .filter(e -> e.getUniqueId().equals(entityId))
                .findFirst().orElse(null);
    }

    private void persist(Shop shop) {
        try {
            shops.update(shop);
        } catch (java.sql.SQLException ex) {
            // Already logged in the service layer; we don't need to abort the chunk-load.
        }
    }
}

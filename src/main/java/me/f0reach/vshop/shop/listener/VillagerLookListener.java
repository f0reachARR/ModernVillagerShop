package me.f0reach.vshop.shop.listener;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.VillagerTeleportGuard;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

/**
 * Makes shop villagers face the nearest player who moves nearby.
 * PlayerMoveEvent-driven so we only scan on relevant activity; teleport goes
 * through {@link VillagerTeleportGuard} to bypass the anchor-teleport cancel
 * in {@link ShopVillagerListener}.
 */
public final class VillagerLookListener implements Listener {

    private static final double IGNORE_MOVE_SQ = 1.0e-4;

    private final ShopRegistry registry;
    private final PluginConfig config;
    private final VillagerTeleportGuard teleportGuard;

    public VillagerLookListener(ShopRegistry registry, PluginConfig config, VillagerTeleportGuard teleportGuard) {
        this.registry = registry;
        this.config = config;
        this.teleportGuard = teleportGuard;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        PluginConfig.VillagerLookConfig look = config.shop().villagerLook();
        if (!look.enabled()) return;

        Location to = event.getTo();
        Location from = event.getFrom();
        // Skip head-only movement (yaw/pitch change with same position).
        if (from.getWorld() == to.getWorld() && from.distanceSquared(to) < IGNORE_MOVE_SQ) return;

        World world = to.getWorld();
        if (world == null) return;
        double radius = look.radius();
        if (radius <= 0) return;
        double radiusSq = radius * radius;

        var nearby = world.getNearbyEntities(to, radius, radius, radius, e -> e instanceof Villager);
        for (Entity e : nearby) {
            if (!(e instanceof Villager villager)) continue;
            if (!registry.isShopVillager(villager.getUniqueId())) continue;
            Location eLoc = villager.getLocation();
            // Getting the entity implies the chunk was loaded when the snapshot
            // was taken, but re-check defensively.
            if (!world.isChunkLoaded(eLoc.getBlockX() >> 4, eLoc.getBlockZ() >> 4)) continue;
            if (to.distanceSquared(eLoc) > radiusSq) continue;
            Vector dir = to.toVector().subtract(eLoc.toVector());
            if (dir.lengthSquared() < 1.0e-6) continue;
            Location target = eLoc.clone().setDirection(dir);
            teleportGuard.withSuppressed(villager.getUniqueId(), () -> villager.teleport(target));
        }
    }
}

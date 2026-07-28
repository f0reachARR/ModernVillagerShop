package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.shop.ShopRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves "which shop is this player looking at?".
 *
 * <p>Villager-backed shops go through {@link Player#getTargetEntity(int)}.
 * NPC-backed shops cannot: FancyNpcs NPCs are packet-only and invisible to the
 * Bukkit entity API — {@code World#getEntities} does not list them and
 * {@code Bukkit#getEntity} cannot find them — so there is nothing for the
 * server-side raycast to hit. Those are resolved by intersecting the player's
 * look vector with a box we place at the shop's anchor ourselves.
 */
public final class ShopTargeting {

    /** Vanilla player hitbox, the shape all but exotic NPC types approximate. */
    private static final double NPC_WIDTH = 0.6;
    private static final double NPC_HEIGHT = 1.8;

    private final ShopRegistry registry;
    private final ShopEntityService entities;

    public ShopTargeting(ShopRegistry registry, ShopEntityService entities) {
        this.registry = registry;
        this.entities = entities;
    }

    /**
     * The shop under the player's crosshair within {@code maxDistance} blocks,
     * whichever backend renders it. Blocks occlude: you cannot target a shop
     * through a wall.
     */
    public Optional<Shop> findFromLineOfSight(Player player, int maxDistance) {
        Entity target = player.getTargetEntity(maxDistance);
        if (target instanceof Villager) {
            Optional<Shop> byEntity = registry.byVillager(target.getUniqueId());
            if (byEntity.isPresent()) return byEntity;
        }
        return findNpcInLineOfSight(player, maxDistance);
    }

    private Optional<Shop> findNpcInLineOfSight(Player player, int maxDistance) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        UUID world = player.getWorld().getUID();

        // Stop the ray at the first solid block so a shop behind a wall is not
        // a valid target, matching how getTargetEntity treats villagers.
        double reach = maxDistance;
        RayTraceResult blocked = player.getWorld().rayTraceBlocks(eye, direction, maxDistance);
        if (blocked != null) {
            reach = blocked.getHitPosition().distance(eye.toVector());
        }

        Shop closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Shop shop : registry.all()) {
            if (!entities.isNpcBacked(shop)) continue;
            ShopLocation at = shop.location();
            if (!world.equals(at.worldId())) continue;

            BoundingBox box = boxFor(shop);
            RayTraceResult hit = box.rayTrace(eye.toVector(), direction, reach);
            if (hit == null) continue;
            double distance = hit.getHitPosition().distance(eye.toVector());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = shop;
            }
        }
        return Optional.ofNullable(closest);
    }

    private BoundingBox boxFor(Shop shop) {
        return hitboxOf(shop.location(), scaleOf(shop));
    }

    /**
     * The NPC's approximate hitbox: centred on the anchor horizontally, standing
     * on it vertically, grown by the shop's scale.
     */
    static BoundingBox hitboxOf(ShopLocation at, float scale) {
        double halfWidth = NPC_WIDTH * scale / 2.0;
        double height = NPC_HEIGHT * scale;
        return new BoundingBox(
                at.x() - halfWidth, at.y(), at.z() - halfWidth,
                at.x() + halfWidth, at.y() + height, at.z() + halfWidth);
    }

    /** Zero and negative scales would collapse the box, so they read as "default". */
    static float normaliseScale(Float scale) {
        return scale == null || scale <= 0 ? 1.0f : scale;
    }

    private float scaleOf(Shop shop) {
        return normaliseScale(entities.appearances().getOrDefault(shop.id()).scale());
    }
}

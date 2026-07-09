package me.f0reach.vshop.shop;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared switch that lets internal callers teleport a shop villager without
 * being cancelled by {@code ShopVillagerListener#onTeleport}. The listener
 * treats any shop-villager teleport as an unwanted move by default; callers
 * that intentionally re-position or re-orient a villager must first mark the
 * entity id here.
 */
public final class VillagerTeleportGuard {

    private final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();

    public void withSuppressed(UUID entityId, Runnable action) {
        suppressed.add(entityId);
        try {
            action.run();
        } finally {
            suppressed.remove(entityId);
        }
    }

    public boolean isSuppressed(UUID entityId) {
        return suppressed.contains(entityId);
    }
}

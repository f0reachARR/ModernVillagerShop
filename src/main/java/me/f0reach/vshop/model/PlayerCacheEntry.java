package me.f0reach.vshop.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of a known player. Populated on join/quit and consulted when
 * rendering player heads in the picker UI without spending a profile lookup.
 * Texture columns are optional — we only persist them if Paper exposes the
 * profile's textures property at runtime.
 */
public record PlayerCacheEntry(
        UUID playerUuid,
        String name,
        String nameLower,
        String textureValue,
        String textureSignature,
        Instant textureUpdatedAt,
        Instant lastSeen,
        Instant updatedAt
) {}

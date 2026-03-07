package me.f0reach.vshop.model;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record Shop(
        int shopId,
        ShopType type,
        UUID villagerUuid,
        @Nullable UUID ownerUuid,
        String world,
        double x,
        double y,
        double z,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}

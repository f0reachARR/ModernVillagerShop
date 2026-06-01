package me.f0reach.vshop.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a slot's running trade-limit consumption within a window. For PER_PLAYER
 * scope, playerUuid is non-null; for GLOBAL scope it is null.
 */
public record TradeLimitUsage(
        UUID slotId,
        UUID playerUuid,
        int amount,
        Instant windowStart
) {}

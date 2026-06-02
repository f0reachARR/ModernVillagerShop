package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.TradeLimitUsage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code shop_limits}. For GLOBAL-scope slots a sentinel UUID
 * ({@link #GLOBAL_KEY}) is used in place of player_uuid.
 */
public interface ShopLimitRepository {

    /** Sentinel for GLOBAL-scope rows (player_uuid is part of the PK). */
    UUID GLOBAL_KEY = new UUID(0L, 0L);

    Optional<TradeLimitUsage> findTx(Connection c, UUID slotId, UUID playerOrGlobal) throws SQLException;

    /** Insert if absent, otherwise add delta to amount. Returns new (amount, window_start). */
    TradeLimitUsage incrementTx(Connection c, UUID slotId, UUID playerOrGlobal, int delta, long windowStartMillis) throws SQLException;

    /** Reset by overwriting amount and window_start (used when window has elapsed). */
    void resetTx(Connection c, UUID slotId, UUID playerOrGlobal, int amount, long windowStartMillis) throws SQLException;

    void deleteAllForSlot(UUID slotId) throws SQLException;
}

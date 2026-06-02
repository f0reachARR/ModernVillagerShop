package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.PlayerCacheEntry;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@code player_cache} table — a denormalised name+texture
 * cache so the player-picker UI can render heads and search without a profile
 * lookup per click.
 */
public interface PlayerCacheRepository {

    /** Upsert by UUID. The {@code lastSeen} and {@code updatedAt} timestamps are caller-supplied. */
    void upsert(PlayerCacheEntry entry) throws SQLException;

    Optional<PlayerCacheEntry> findByUuid(UUID playerUuid) throws SQLException;

    /** Look up by exact-case-insensitive name. */
    Optional<PlayerCacheEntry> findByName(String name) throws SQLException;

    /**
     * Returns a page sorted by {@code lastSeen DESC} (or {@code name_lower ASC}
     * if {@code byName} is true). Used to populate the picker UI.
     */
    List<PlayerCacheEntry> page(int offset, int limit, boolean byName) throws SQLException;

    /** Substring search on name_lower. Returns {@code limit} hits sorted by relevance (prefix matches first). */
    List<PlayerCacheEntry> search(String query, int limit) throws SQLException;

    int count() throws SQLException;
}

package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.model.PlayerCacheEntry;
import me.f0reach.vshop.storage.repo.PlayerCacheRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SqlitePlayerCacheRepository implements PlayerCacheRepository {

    private final DataSource dataSource;

    public SqlitePlayerCacheRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void upsert(PlayerCacheEntry entry) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_cache (player_uuid, name, name_lower, texture_value, texture_signature, " +
                             "texture_updated_at, last_seen, updated_at) VALUES (?,?,?,?,?,?,?,?) " +
                             "ON CONFLICT(player_uuid) DO UPDATE SET " +
                             "name = excluded.name, name_lower = excluded.name_lower, " +
                             "texture_value = COALESCE(excluded.texture_value, player_cache.texture_value), " +
                             "texture_signature = COALESCE(excluded.texture_signature, player_cache.texture_signature), " +
                             "texture_updated_at = COALESCE(excluded.texture_updated_at, player_cache.texture_updated_at), " +
                             "last_seen = excluded.last_seen, updated_at = excluded.updated_at")) {
            ps.setString(1, entry.playerUuid().toString());
            ps.setString(2, entry.name());
            ps.setString(3, entry.nameLower());
            ps.setString(4, entry.textureValue());
            ps.setString(5, entry.textureSignature());
            if (entry.textureUpdatedAt() == null) ps.setNull(6, java.sql.Types.INTEGER);
            else ps.setLong(6, entry.textureUpdatedAt().toEpochMilli());
            ps.setLong(7, entry.lastSeen().toEpochMilli());
            ps.setLong(8, entry.updatedAt().toEpochMilli());
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<PlayerCacheEntry> findByUuid(UUID playerUuid) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM player_cache WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<PlayerCacheEntry> findByName(String name) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM player_cache WHERE name_lower = ? LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<PlayerCacheEntry> page(int offset, int limit, boolean byName) throws SQLException {
        String order = byName ? "name_lower ASC" : "last_seen DESC";
        List<PlayerCacheEntry> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM player_cache ORDER BY " + order + " LIMIT ? OFFSET ?")) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public List<PlayerCacheEntry> search(String query, int limit) throws SQLException {
        String q = query.toLowerCase(Locale.ROOT);
        List<PlayerCacheEntry> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM player_cache WHERE name_lower LIKE ? " +
                             "ORDER BY CASE WHEN name_lower LIKE ? THEN 0 ELSE 1 END, last_seen DESC LIMIT ?")) {
            ps.setString(1, "%" + q + "%");
            ps.setString(2, q + "%");
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public int count() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM player_cache");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static PlayerCacheEntry map(ResultSet rs) throws SQLException {
        long texUpdated = rs.getLong("texture_updated_at");
        boolean texNull = rs.wasNull();
        return new PlayerCacheEntry(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("name"),
                rs.getString("name_lower"),
                rs.getString("texture_value"),
                rs.getString("texture_signature"),
                texNull ? null : Instant.ofEpochMilli(texUpdated),
                Instant.ofEpochMilli(rs.getLong("last_seen")),
                Instant.ofEpochMilli(rs.getLong("updated_at"))
        );
    }
}

package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.model.TradeLimitUsage;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SqliteShopLimitRepository implements ShopLimitRepository {

    private final DataSource dataSource;

    public SqliteShopLimitRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<TradeLimitUsage> findTx(Connection c, UUID slotId, UUID playerOrGlobal) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT amount, window_start FROM shop_limits WHERE slot_id = ? AND player_uuid = ?")) {
            ps.setString(1, slotId.toString());
            ps.setString(2, playerOrGlobal.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new TradeLimitUsage(slotId, playerOrGlobal,
                        rs.getInt(1), Instant.ofEpochMilli(rs.getLong(2))));
            }
        }
    }

    @Override
    public TradeLimitUsage incrementTx(Connection c, UUID slotId, UUID playerOrGlobal, int delta,
                                       long windowStartMillis) throws SQLException {
        Optional<TradeLimitUsage> existing = findTx(c, slotId, playerOrGlobal);
        if (existing.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO shop_limits (slot_id, player_uuid, amount, window_start) VALUES (?,?,?,?)")) {
                ps.setString(1, slotId.toString());
                ps.setString(2, playerOrGlobal.toString());
                ps.setInt(3, delta);
                ps.setLong(4, windowStartMillis);
                ps.executeUpdate();
            }
            return new TradeLimitUsage(slotId, playerOrGlobal, delta, Instant.ofEpochMilli(windowStartMillis));
        }
        int newAmount = existing.get().amount() + delta;
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE shop_limits SET amount = ? WHERE slot_id = ? AND player_uuid = ?")) {
            ps.setInt(1, newAmount);
            ps.setString(2, slotId.toString());
            ps.setString(3, playerOrGlobal.toString());
            ps.executeUpdate();
        }
        return new TradeLimitUsage(slotId, playerOrGlobal, newAmount, existing.get().windowStart());
    }

    @Override
    public void resetTx(Connection c, UUID slotId, UUID playerOrGlobal, int amount, long windowStartMillis) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO shop_limits (slot_id, player_uuid, amount, window_start) VALUES (?,?,?,?) " +
                        "ON CONFLICT(slot_id, player_uuid) DO UPDATE SET amount=excluded.amount, window_start=excluded.window_start")) {
            ps.setString(1, slotId.toString());
            ps.setString(2, playerOrGlobal.toString());
            ps.setInt(3, amount);
            ps.setLong(4, windowStartMillis);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteAllForSlot(UUID slotId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shop_limits WHERE slot_id = ?")) {
            ps.setString(1, slotId.toString());
            ps.executeUpdate();
        }
    }
}

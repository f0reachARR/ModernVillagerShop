package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ListingRepository {
    private final DatabaseManager db;

    public ListingRepository(DatabaseManager db) {
        this.db = db;
    }

    public int create(int shopId, int uiSlot, ListingMode mode, byte[] itemSerialized,
                      double unitPrice, int tradeQuantity, int stock, int targetStock,
                      int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException {
        String sql = "INSERT INTO listings (shop_id, ui_slot, mode, item_serialized, unit_price, trade_qty, stock, target_stock, "
                + "cooldown_seconds, lifetime_limit_per_player) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, shopId);
            ps.setInt(2, uiSlot);
            ps.setString(3, mode.name());
            ps.setBytes(4, itemSerialized);
            ps.setDouble(5, unitPrice);
            ps.setInt(6, tradeQuantity);
            ps.setInt(7, stock);
            ps.setInt(8, targetStock);
            ps.setInt(9, cooldownSeconds);
            ps.setInt(10, lifetimeLimitPerPlayer);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Failed to retrieve generated listing_id");
    }

    public List<Listing> findByShopId(int shopId) throws SQLException {
        String sql = "SELECT * FROM listings WHERE shop_id = ? ORDER BY ui_slot ASC";
        List<Listing> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Optional<Listing> findById(int listingId) throws SQLException {
        String sql = "SELECT * FROM listings WHERE listing_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        }
        return Optional.empty();
    }

    public int countByShopId(int shopId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM listings WHERE shop_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public boolean existsByShopIdAndSlot(int shopId, int uiSlot) throws SQLException {
        String sql = "SELECT 1 FROM listings WHERE shop_id = ? AND ui_slot = ? LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setInt(2, uiSlot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void updateListingDetails(int listingId, double unitPrice, int tradeQuantity, int stock, int targetStock,
                                     int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException {
        String sql = "UPDATE listings SET unit_price = ?, trade_qty = ?, stock = ?, target_stock = ?, cooldown_seconds = ?, "
                + "lifetime_limit_per_player = ?, updated_at = ? WHERE listing_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, unitPrice);
            ps.setInt(2, tradeQuantity);
            ps.setInt(3, stock);
            ps.setInt(4, targetStock);
            ps.setInt(5, cooldownSeconds);
            ps.setInt(6, lifetimeLimitPerPlayer);
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.setInt(8, listingId);
            ps.executeUpdate();
        }
    }

    public void setEnabled(int listingId, boolean enabled) throws SQLException {
        String sql = "UPDATE listings SET enabled = ?, updated_at = ? WHERE listing_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, listingId);
            ps.executeUpdate();
        }
    }

    public void delete(int listingId) throws SQLException {
        String sql = "DELETE FROM listings WHERE listing_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.executeUpdate();
        }
    }

    /**
     * Atomically decrement stock. Returns true if stock was available.
     */
    public boolean decrementStock(int listingId, int amount) throws SQLException {
        String sql = "UPDATE listings SET stock = stock - ?, updated_at = ? WHERE listing_id = ? AND stock >= ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, listingId);
            ps.setInt(4, amount);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Atomically increment stock for BUY orders. Returns true if target not yet reached.
     */
    public boolean incrementStock(int listingId, int amount) throws SQLException {
        String sql = "UPDATE listings SET stock = stock + ?, updated_at = ? WHERE listing_id = ? AND stock + ? <= target_stock";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, listingId);
            ps.setInt(4, amount);
            return ps.executeUpdate() > 0;
        }
    }

    private Listing mapRow(ResultSet rs) throws SQLException {
        return new Listing(
                rs.getInt("listing_id"),
                rs.getInt("shop_id"),
                rs.getInt("ui_slot"),
                ListingMode.valueOf(rs.getString("mode")),
                rs.getBytes("item_serialized"),
                rs.getDouble("unit_price"),
                rs.getInt("trade_qty"),
                rs.getInt("stock"),
                rs.getInt("target_stock"),
                rs.getInt("cooldown_seconds"),
                rs.getInt("lifetime_limit_per_player"),
                rs.getBoolean("enabled"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}

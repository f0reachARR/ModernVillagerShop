package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingWithAccess;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.TradeAccessBlockReason;
import me.f0reach.vshop.model.TradeAccessSnapshot;
import me.f0reach.vshop.storage.ConnectionProvider;
import me.f0reach.vshop.storage.ListingRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MysqlListingRepository implements ListingRepository {
    private final ConnectionProvider connectionProvider;

    public MysqlListingRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public int create(int shopId, int uiSlot, ListingMode mode, byte[] itemSerialized,
                      double unitPrice, int tradeQuantity, int stock, int targetStock,
                      int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException {
        String sql = "INSERT INTO listings (shop_id, ui_slot, mode, item_serialized, unit_price, trade_qty, stock, target_stock, "
                + "cooldown_seconds, lifetime_limit_per_player) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
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
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated listing_id");
    }

    @Override
    public List<Listing> findByShopId(int shopId) throws SQLException {
        String sql = "SELECT * FROM listings WHERE shop_id = ? ORDER BY ui_slot ASC";
        List<Listing> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<ListingWithAccess> findDisplayEntriesByShopId(int shopId, UUID playerUuid, Instant now) throws SQLException {
        String sql = "SELECT "
                + "l.listing_id, l.shop_id, l.ui_slot, l.mode, l.item_serialized, l.unit_price, l.trade_qty, "
                + "l.stock, l.target_stock, l.cooldown_seconds, l.lifetime_limit_per_player, l.enabled, l.updated_at, "
                + "COALESCE(SUM(CASE WHEN t.tx_id IS NOT NULL THEN 1 ELSE 0 END), 0) AS total_trade_count, "
                + "COALESCE(SUM(CASE WHEN t.tx_id IS NOT NULL AND UNIX_TIMESTAMP(t.created_at) >= ? - l.cooldown_seconds "
                + "THEN 1 ELSE 0 END), 0) AS window_trade_count, "
                + "MIN(CASE WHEN t.tx_id IS NOT NULL AND UNIX_TIMESTAMP(t.created_at) >= ? - l.cooldown_seconds "
                + "THEN UNIX_TIMESTAMP(t.created_at) END) AS window_oldest_trade_epoch "
                + "FROM listings l "
                + "LEFT JOIN transactions t ON t.listing_id = l.listing_id AND t.buyer_uuid = ? "
                + "WHERE l.shop_id = ? "
                + "GROUP BY l.listing_id, l.shop_id, l.ui_slot, l.mode, l.item_serialized, l.unit_price, l.trade_qty, "
                + "l.stock, l.target_stock, l.cooldown_seconds, l.lifetime_limit_per_player, l.enabled, l.updated_at "
                + "ORDER BY l.ui_slot ASC";
        List<ListingWithAccess> list = new ArrayList<>();
        long nowEpochSecond = now.getEpochSecond();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, nowEpochSecond);
            ps.setLong(2, nowEpochSecond);
            ps.setString(3, playerUuid.toString());
            ps.setInt(4, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Listing listing = mapRow(rs);
                    list.add(new ListingWithAccess(listing, mapAccess(rs, listing, nowEpochSecond)));
                }
            }
        }
        return list;
    }

    @Override
    public Optional<Listing> findById(int listingId) throws SQLException {
        String sql = "SELECT * FROM listings WHERE listing_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int countByShopId(int shopId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM listings WHERE shop_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public boolean existsByShopIdAndSlot(int shopId, int uiSlot) throws SQLException {
        String sql = "SELECT 1 FROM listings WHERE shop_id = ? AND ui_slot = ? LIMIT 1";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setInt(2, uiSlot);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public void updateListingDetails(int listingId, double unitPrice, int tradeQuantity, int stock, int targetStock,
                                     int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException {
        String sql = "UPDATE listings SET unit_price = ?, trade_qty = ?, stock = ?, target_stock = ?, cooldown_seconds = ?, "
                + "lifetime_limit_per_player = ?, updated_at = ? WHERE listing_id = ?";
        try (Connection conn = connectionProvider.getConnection();
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

    @Override
    public void setEnabled(int listingId, boolean enabled) throws SQLException {
        String sql = "UPDATE listings SET enabled = ?, updated_at = ? WHERE listing_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, enabled);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, listingId);
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(int listingId) throws SQLException {
        String sql = "DELETE FROM listings WHERE listing_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.executeUpdate();
        }
    }

    @Override
    public boolean decrementStock(int listingId, int amount) throws SQLException {
        String sql = "UPDATE listings SET stock = stock - ?, updated_at = ? WHERE listing_id = ? AND stock >= ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, amount);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setInt(3, listingId);
            ps.setInt(4, amount);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public boolean incrementStock(int listingId, int amount) throws SQLException {
        String sql = "UPDATE listings SET stock = stock + ?, updated_at = ? WHERE listing_id = ? AND stock + ? <= target_stock";
        try (Connection conn = connectionProvider.getConnection();
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

    private TradeAccessSnapshot mapAccess(ResultSet rs, Listing listing, long nowEpochSecond) throws SQLException {
        if (listing.cooldownSeconds() <= 0 && listing.lifetimeLimitPerPlayer() <= 0) {
            return TradeAccessSnapshot.unrestricted();
        }

        int limit = Math.max(1, listing.lifetimeLimitPerPlayer());
        int totalTradeCount = rs.getInt("total_trade_count");
        int windowTradeCount = rs.getInt("window_trade_count");
        int countBasis = listing.cooldownSeconds() > 0 ? windowTradeCount : totalTradeCount;
        int remainingLifetimeTrades = Math.max(0, limit - countBasis);

        if (listing.cooldownSeconds() > 0 && windowTradeCount >= limit) {
            long oldestEpochSecond = rs.getLong("window_oldest_trade_epoch");
            int remainingCooldownSeconds = 0;
            if (!rs.wasNull()) {
                remainingCooldownSeconds = (int) Math.max(0,
                        oldestEpochSecond + listing.cooldownSeconds() - nowEpochSecond);
            }
            return new TradeAccessSnapshot(
                    TradeAccessBlockReason.COOLDOWN_ACTIVE,
                    remainingCooldownSeconds,
                    remainingLifetimeTrades
            );
        }

        if (listing.cooldownSeconds() == 0 && totalTradeCount >= limit) {
            return new TradeAccessSnapshot(
                    TradeAccessBlockReason.LIFETIME_LIMIT_REACHED,
                    0,
                    remainingLifetimeTrades
            );
        }

        return new TradeAccessSnapshot(
                TradeAccessBlockReason.NONE,
                0,
                remainingLifetimeTrades
        );
    }
}

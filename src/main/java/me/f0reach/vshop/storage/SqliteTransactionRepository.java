package me.f0reach.vshop.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class SqliteTransactionRepository implements TransactionRepository {
    private final ConnectionProvider connectionProvider;

    public SqliteTransactionRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public void record(int shopId, int listingId, String direction,
            UUID buyerUuid, UUID sellerUuid,
            int qty, double gross, double fee, double net) throws SQLException {
        String sql = "INSERT INTO transactions (shop_id, listing_id, direction, buyer_uuid, seller_uuid, qty, gross, fee, net) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = connectionProvider.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setInt(2, listingId);
            ps.setString(3, direction);
            ps.setString(4, buyerUuid.toString());
            ps.setString(5, sellerUuid != null ? sellerUuid.toString() : null);
            ps.setInt(6, qty);
            ps.setDouble(7, gross);
            ps.setDouble(8, fee);
            ps.setDouble(9, net);
            ps.executeUpdate();
        }
    }

    @Override
    public Optional<Instant> findOldestTradeTimeForPlayer(int listingId, UUID playerUuid, Instant since)
            throws SQLException {
        String sql = "SELECT MAX(created_at) AS trade_at FROM transactions WHERE listing_id = ? AND buyer_uuid = ? AND DATETIME(created_at) >= DATETIME(?)";
        try (Connection conn = connectionProvider.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.setString(2, playerUuid.toString());
            ps.setTimestamp(3, Timestamp.from(since));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                Timestamp timestamp = rs.getTimestamp("trade_at");
                return timestamp == null ? Optional.empty() : Optional.of(timestamp.toInstant());
            }
        }
    }

    @Override
    public int countTradesForPlayer(int listingId, UUID playerUuid) throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions WHERE listing_id = ? AND buyer_uuid = ?";
        try (Connection conn = connectionProvider.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.setString(2, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    @Override
    public int countTradesForPlayerSince(int listingId, UUID playerUuid, Instant since) throws SQLException {
        String sql = "SELECT COUNT(*) FROM transactions WHERE listing_id = ? AND buyer_uuid = ? AND DATETIME(created_at) >= DATETIME(?)";
        try (Connection conn = connectionProvider.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, listingId);
            ps.setString(2, playerUuid.toString());
            ps.setTimestamp(3, Timestamp.from(since));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}

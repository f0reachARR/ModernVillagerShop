package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.storage.repo.ShopNotificationRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MysqlShopNotificationRepository implements ShopNotificationRepository {

    private final DataSource dataSource;

    public MysqlShopNotificationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void queueTx(Connection c, UUID playerUuid, UUID shopId, Long transactionId, int count,
                        BigDecimal totalAmount) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO shop_notifications (player_uuid, shop_id, transaction_id, count, total_amount, " +
                        "created_at, delivered) VALUES (?,?,?,?,?,?,0)")) {
            ps.setString(1, playerUuid.toString());
            if (shopId == null) ps.setNull(2, Types.VARCHAR);
            else ps.setString(2, shopId.toString());
            if (transactionId == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, transactionId);
            ps.setInt(4, count);
            ps.setBigDecimal(5, totalAmount);
            ps.setLong(6, Instant.now().toEpochMilli());
            ps.executeUpdate();
        }
    }

    @Override
    public List<Pending> findUndelivered(UUID playerUuid) throws SQLException {
        List<Pending> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, shop_id, transaction_id, count, total_amount, created_at FROM shop_notifications " +
                             "WHERE player_uuid = ? AND delivered = 0 ORDER BY created_at")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String shopStr = rs.getString("shop_id");
                    UUID shopId = shopStr == null ? null : UUID.fromString(shopStr);
                    long txId = rs.getLong("transaction_id");
                    Long transactionId = rs.wasNull() ? null : txId;
                    int count = rs.getInt("count");
                    BigDecimal totalAmount = rs.getBigDecimal("total_amount");
                    Instant createdAt = Instant.ofEpochMilli(rs.getLong("created_at"));
                    out.add(new Pending(id, shopId, transactionId, count, totalAmount, createdAt));
                }
            }
        }
        return out;
    }

    @Override
    public void markDelivered(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shop_notifications SET delivered = 1 WHERE id = ?")) {
            for (long id : ids) {
                ps.setLong(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}

package me.f0reach.vshop.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class TransactionRepository {
    private final DatabaseManager db;

    public TransactionRepository(DatabaseManager db) {
        this.db = db;
    }

    public void record(int shopId, int listingId, String direction,
                       UUID buyerUuid, UUID sellerUuid,
                       int qty, double gross, double fee, double net) throws SQLException {
        String sql = "INSERT INTO transactions (shop_id, listing_id, direction, buyer_uuid, seller_uuid, qty, gross, fee, net) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = db.getConnection();
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
}

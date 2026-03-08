package me.f0reach.vshop.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class MysqlShopInventoryRepository implements ShopInventoryRepository {
    private final ConnectionProvider connectionProvider;

    public MysqlShopInventoryRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Map<Integer, byte[]> findByShopId(int shopId) throws SQLException {
        String sql = "SELECT slot_index, item_serialized FROM shop_inventory_slots WHERE shop_id = ? ORDER BY slot_index ASC";
        Map<Integer, byte[]> slots = new HashMap<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    slots.put(rs.getInt("slot_index"), rs.getBytes("item_serialized"));
                }
            }
        }
        return slots;
    }

    @Override
    public void upsertSlot(int shopId, int slotIndex, byte[] itemSerialized) throws SQLException {
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(upsertSlotSql())) {
            ps.setInt(1, shopId);
            ps.setInt(2, slotIndex);
            ps.setBytes(3, itemSerialized);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteSlot(int shopId, int slotIndex) throws SQLException {
        String sql = "DELETE FROM shop_inventory_slots WHERE shop_id = ? AND slot_index = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setInt(2, slotIndex);
            ps.executeUpdate();
        }
    }

    String upsertSlotSql() {
        return "INSERT INTO shop_inventory_slots (shop_id, slot_index, item_serialized) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE item_serialized = VALUES(item_serialized)";
    }
}

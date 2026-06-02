package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.item.ItemStackCodec;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.storage.repo.ShopInventoryRepository;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteShopInventoryRepository implements ShopInventoryRepository {

    private final DataSource dataSource;

    public SqliteShopInventoryRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<InventoryEntry> findByShop(UUID shopId) throws SQLException {
        List<InventoryEntry> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT shop_id, slot_index, item_data, amount FROM shop_inventory " +
                             "WHERE shop_id = ? ORDER BY slot_index")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public Optional<InventoryEntry> findSlot(UUID shopId, int slotIndex) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT shop_id, slot_index, item_data, amount FROM shop_inventory " +
                             "WHERE shop_id = ? AND slot_index = ?")) {
            ps.setString(1, shopId.toString());
            ps.setInt(2, slotIndex);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public void upsert(InventoryEntry entry) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO shop_inventory (shop_id, slot_index, item_data, amount) " +
                             "VALUES (?,?,?,?) " +
                             "ON CONFLICT(shop_id, slot_index) DO UPDATE SET item_data=excluded.item_data, amount=excluded.amount")) {
            ps.setString(1, entry.shopId().toString());
            ps.setInt(2, entry.slotIndex());
            ps.setBytes(3, ItemStackCodec.encode(entry.item()));
            ps.setInt(4, entry.amount());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(UUID shopId, int slotIndex) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM shop_inventory WHERE shop_id = ? AND slot_index = ?")) {
            ps.setString(1, shopId.toString());
            ps.setInt(2, slotIndex);
            ps.executeUpdate();
        }
    }

    @Override
    public int addAmountTx(Connection c, UUID shopId, int slotIndex, ItemStack item, int delta) throws SQLException {
        int existing = 0;
        boolean present = false;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT amount FROM shop_inventory WHERE shop_id = ? AND slot_index = ?")) {
            ps.setString(1, shopId.toString());
            ps.setInt(2, slotIndex);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existing = rs.getInt(1);
                    present = true;
                }
            }
        }
        int newAmount = existing + delta;
        if (newAmount < 0) throw new SQLException("inventory amount would go negative");
        if (newAmount == 0 && present) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM shop_inventory WHERE shop_id = ? AND slot_index = ?")) {
                ps.setString(1, shopId.toString());
                ps.setInt(2, slotIndex);
                ps.executeUpdate();
            }
            return 0;
        }
        if (present) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE shop_inventory SET amount = ? WHERE shop_id = ? AND slot_index = ?")) {
                ps.setInt(1, newAmount);
                ps.setString(2, shopId.toString());
                ps.setInt(3, slotIndex);
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO shop_inventory (shop_id, slot_index, item_data, amount) VALUES (?,?,?,?)")) {
                ps.setString(1, shopId.toString());
                ps.setInt(2, slotIndex);
                ps.setBytes(3, ItemStackCodec.encode(item));
                ps.setInt(4, newAmount);
                ps.executeUpdate();
            }
        }
        return newAmount;
    }

    @Override
    public int sumMatchingTx(Connection c, UUID shopId, ItemStack itemTemplate) throws SQLException {
        int total = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT item_data, amount FROM shop_inventory WHERE shop_id = ?")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack stored = ItemStackCodec.decode(rs.getBytes(1));
                    if (ItemIdentity.sameItem(stored, itemTemplate)) {
                        total += rs.getInt(2);
                    }
                }
            }
        }
        return total;
    }

    @Override
    public void removeMatchingTx(Connection c, UUID shopId, ItemStack itemTemplate, int deltaTotal) throws SQLException {
        if (deltaTotal <= 0) return;
        // Pull candidates ordered by largest first so we touch fewer rows.
        record Row(int slotIndex, int amount) {}
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT slot_index, item_data, amount FROM shop_inventory WHERE shop_id = ? ORDER BY amount DESC")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemStack stored = ItemStackCodec.decode(rs.getBytes(2));
                    if (ItemIdentity.sameItem(stored, itemTemplate)) {
                        rows.add(new Row(rs.getInt(1), rs.getInt(3)));
                    }
                }
            }
        }
        int remaining = deltaTotal;
        for (Row r : rows) {
            if (remaining <= 0) break;
            int take = Math.min(r.amount(), remaining);
            int newAmount = r.amount() - take;
            if (newAmount == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM shop_inventory WHERE shop_id = ? AND slot_index = ?")) {
                    ps.setString(1, shopId.toString());
                    ps.setInt(2, r.slotIndex());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE shop_inventory SET amount = ? WHERE shop_id = ? AND slot_index = ?")) {
                    ps.setInt(1, newAmount);
                    ps.setString(2, shopId.toString());
                    ps.setInt(3, r.slotIndex());
                    ps.executeUpdate();
                }
            }
            remaining -= take;
        }
        if (remaining > 0) {
            throw new SQLException("not enough matching items in shop inventory (missing " + remaining + ")");
        }
    }

    private static InventoryEntry map(ResultSet rs) throws SQLException {
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        int slotIndex = rs.getInt("slot_index");
        ItemStack item = ItemStackCodec.decode(rs.getBytes("item_data"));
        int amount = rs.getInt("amount");
        return new InventoryEntry(shopId, slotIndex, item, amount);
    }
}

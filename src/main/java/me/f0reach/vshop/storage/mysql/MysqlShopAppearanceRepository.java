package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.item.ItemStackCodec;
import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.storage.repo.ShopAppearanceRepository;
import me.f0reach.vshop.storage.repo.ShopAppearanceSql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MysqlShopAppearanceRepository implements ShopAppearanceRepository {

    private final DataSource dataSource;

    public MysqlShopAppearanceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ShopAppearance> find(UUID shopId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            ShopAppearance appearance = null;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + ShopAppearanceSql.COLUMNS + " FROM shop_appearance WHERE shop_id = ?")) {
                ps.setString(1, shopId.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) appearance = ShopAppearanceSql.mapAppearance(rs);
                }
            }
            if (appearance == null) return Optional.empty();
            loadEquipment(c, shopId, appearance);
            return Optional.of(appearance);
        }
    }

    @Override
    public List<ShopAppearance> findAll() throws SQLException {
        Map<UUID, ShopAppearance> byShop = new LinkedHashMap<>();
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + ShopAppearanceSql.COLUMNS + " FROM shop_appearance");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ShopAppearance a = ShopAppearanceSql.mapAppearance(rs);
                    byShop.put(a.shopId(), a);
                }
            }
            if (byShop.isEmpty()) return List.of();
            // One sweep over the child table beats a query per shop on enable.
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT shop_id, slot, item_data FROM shop_appearance_equipment");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ShopAppearance a = byShop.get(UUID.fromString(rs.getString("shop_id")));
                    if (a == null) continue; // orphan row; ignored, delete() keeps these from accruing
                    a.equipment().put(rs.getString("slot"), ItemStackCodec.decode(rs.getBytes("item_data")));
                }
            }
        }
        return new ArrayList<>(byShop.values());
    }

    @Override
    public void upsert(ShopAppearance appearance) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO shop_appearance (" + ShopAppearanceSql.COLUMNS + ") " +
                                "VALUES (" + ShopAppearanceSql.PLACEHOLDERS + ") " +
                                "ON DUPLICATE KEY UPDATE " +
                                "backend=VALUES(backend), entity_type=VALUES(entity_type), " +
                                "skin=VALUES(skin), skin_variant=VALUES(skin_variant), " +
                                "glowing=VALUES(glowing), glow_color=VALUES(glow_color), " +
                                "scale=VALUES(scale), turn_to_player=VALUES(turn_to_player), " +
                                "attributes=VALUES(attributes), updated_at=VALUES(updated_at)")) {
                    ShopAppearanceSql.bindAppearance(ps, appearance);
                    ps.executeUpdate();
                }
                replaceEquipment(c, appearance);
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    @Override
    public void delete(UUID shopId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                deleteEquipment(c, shopId);
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM shop_appearance WHERE shop_id = ?")) {
                    ps.setString(1, shopId.toString());
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void loadEquipment(Connection c, UUID shopId, ShopAppearance into) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT slot, item_data FROM shop_appearance_equipment WHERE shop_id = ? ORDER BY slot")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    into.equipment().put(rs.getString("slot"), ItemStackCodec.decode(rs.getBytes("item_data")));
                }
            }
        }
    }

    /** The equipment set is replaced wholesale so a removed slot actually disappears. */
    private static void replaceEquipment(Connection c, ShopAppearance appearance) throws SQLException {
        deleteEquipment(c, appearance.shopId());
        if (appearance.equipment().isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO shop_appearance_equipment (shop_id, slot, item_data) VALUES (?,?,?)")) {
            for (var entry : appearance.equipment().entrySet()) {
                if (entry.getValue() == null) continue;
                ps.setString(1, appearance.shopId().toString());
                ps.setString(2, entry.getKey());
                ps.setBytes(3, ItemStackCodec.encode(entry.getValue()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void deleteEquipment(Connection c, UUID shopId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM shop_appearance_equipment WHERE shop_id = ?")) {
            ps.setString(1, shopId.toString());
            ps.executeUpdate();
        }
    }
}

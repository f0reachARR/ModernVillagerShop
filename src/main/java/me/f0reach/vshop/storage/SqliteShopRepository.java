package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;

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

public final class SqliteShopRepository implements ShopRepository {
    private final ConnectionProvider connectionProvider;

    public SqliteShopRepository(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public int create(ShopType type, UUID villagerUuid, UUID ownerUuid,
                      String world, double x, double y, double z) throws SQLException {
        String sql = "INSERT INTO shops (type, villager_uuid, owner_uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?, ?)";
        UUID persistedOwnerUuid = type == ShopType.ADMIN ? null : ownerUuid;
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.name());
            ps.setString(2, villagerUuid.toString());
            ps.setString(3, persistedOwnerUuid != null ? persistedOwnerUuid.toString() : null);
            ps.setString(4, world);
            ps.setDouble(5, x);
            ps.setDouble(6, y);
            ps.setDouble(7, z);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to retrieve generated shop_id");
    }

    @Override
    public Optional<Shop> findById(int shopId) throws SQLException {
        String sql = "SELECT * FROM shops WHERE shop_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Shop> findByVillagerUuid(UUID villagerUuid) throws SQLException {
        String sql = "SELECT * FROM shops WHERE villager_uuid = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, villagerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Shop> findAll() throws SQLException {
        String sql = "SELECT * FROM shops WHERE active = 1";
        List<Shop> list = new ArrayList<>();
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public void delete(int shopId) throws SQLException {
        String sql = "UPDATE shops SET active = 0, updated_at = ? WHERE shop_id = ?";
        try (Connection conn = connectionProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setInt(2, shopId);
            ps.executeUpdate();
        }
    }

    private Shop mapRow(ResultSet rs) throws SQLException {
        ShopType shopType = ShopType.valueOf(rs.getString("type"));
        String ownerStr = rs.getString("owner_uuid");
        UUID ownerUuid = shopType == ShopType.ADMIN
                ? null
                : (ownerStr != null ? UUID.fromString(ownerStr) : null);
        return new Shop(
                rs.getInt("shop_id"),
                shopType,
                UUID.fromString(rs.getString("villager_uuid")),
                ownerUuid,
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}

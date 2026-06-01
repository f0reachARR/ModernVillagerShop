package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.storage.repo.ShopRepository;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Villager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MysqlShopRepository implements ShopRepository {

    private final DataSource dataSource;

    public MysqlShopRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Shop> findById(UUID id) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shops WHERE id = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Shop> findAll() throws SQLException {
        List<Shop> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shops");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(map(rs));
        }
        return out;
    }

    @Override
    public List<Shop> findByOwner(UUID ownerUuid) throws SQLException {
        return queryByColumn("owner_uuid", ownerUuid);
    }

    @Override
    public List<Shop> findInWorld(UUID worldId) throws SQLException {
        return queryByColumn("world_id", worldId);
    }

    private List<Shop> queryByColumn(String column, UUID value) throws SQLException {
        List<Shop> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shops WHERE " + column + " = ?")) {
            ps.setString(1, value.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public void insert(Shop shop) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO shops (id,type,owner_uuid,world_id,x,y,z,yaw,pitch,villager_entity_id," +
                             "profession,name,suspended,row_count,created_at,updated_at) " +
                             "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            int i = 1;
            ps.setString(i++, shop.id().toString());
            ps.setString(i++, shop.type().name());
            setNullableString(ps, i++, uuidStr(shop.ownerUuid()));
            ps.setString(i++, shop.location().worldId().toString());
            ps.setDouble(i++, shop.location().x());
            ps.setDouble(i++, shop.location().y());
            ps.setDouble(i++, shop.location().z());
            ps.setDouble(i++, shop.location().yaw());
            ps.setDouble(i++, shop.location().pitch());
            setNullableString(ps, i++, uuidStr(shop.villagerEntityId()));
            setNullableString(ps, i++, professionKey(shop.profession()));
            ps.setString(i++, shop.name());
            ps.setInt(i++, shop.suspended() ? 1 : 0);
            ps.setInt(i++, shop.rowCount());
            ps.setLong(i++, shop.createdAt().toEpochMilli());
            ps.setLong(i, shop.updatedAt().toEpochMilli());
            ps.executeUpdate();
        }
    }

    @Override
    public void update(Shop shop) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shops SET type=?, owner_uuid=?, world_id=?, x=?, y=?, z=?, yaw=?, pitch=?, " +
                             "villager_entity_id=?, profession=?, name=?, suspended=?, row_count=?, updated_at=? " +
                             "WHERE id=?")) {
            int i = 1;
            ps.setString(i++, shop.type().name());
            setNullableString(ps, i++, uuidStr(shop.ownerUuid()));
            ps.setString(i++, shop.location().worldId().toString());
            ps.setDouble(i++, shop.location().x());
            ps.setDouble(i++, shop.location().y());
            ps.setDouble(i++, shop.location().z());
            ps.setDouble(i++, shop.location().yaw());
            ps.setDouble(i++, shop.location().pitch());
            setNullableString(ps, i++, uuidStr(shop.villagerEntityId()));
            setNullableString(ps, i++, professionKey(shop.profession()));
            ps.setString(i++, shop.name());
            ps.setInt(i++, shop.suspended() ? 1 : 0);
            ps.setInt(i++, shop.rowCount());
            ps.setLong(i++, shop.updatedAt().toEpochMilli());
            ps.setString(i, shop.id().toString());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(UUID shopId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                deleteOn(c, "shop_co_owners", "shop_id", shopId);
                deleteOn(c, "shop_slots", "shop_id", shopId);
                deleteOn(c, "shop_inventory", "shop_id", shopId);
                deleteOn(c, "shop_transactions", "shop_id", shopId);
                deleteOn(c, "shop_notifications", "shop_id", shopId);
                deleteOn(c, "shops", "id", shopId);
                c.commit();
            } catch (SQLException ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static void deleteOn(Connection c, String table, String column, UUID id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + table + " WHERE " + column + " = ?")) {
            ps.setString(1, id.toString());
            ps.executeUpdate();
        }
    }

    private static Shop map(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        ShopType type = ShopType.valueOf(rs.getString("type"));
        String ownerStr = rs.getString("owner_uuid");
        UUID owner = ownerStr == null ? null : UUID.fromString(ownerStr);
        UUID world = UUID.fromString(rs.getString("world_id"));
        ShopLocation loc = new ShopLocation(world,
                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                (float) rs.getDouble("yaw"), (float) rs.getDouble("pitch"));
        String villagerStr = rs.getString("villager_entity_id");
        UUID villager = villagerStr == null ? null : UUID.fromString(villagerStr);
        Villager.Profession profession = parseProfession(rs.getString("profession"));
        String name = rs.getString("name");
        boolean suspended = rs.getInt("suspended") != 0;
        int rows = rs.getInt("row_count");
        Instant created = Instant.ofEpochMilli(rs.getLong("created_at"));
        Instant updated = Instant.ofEpochMilli(rs.getLong("updated_at"));
        return new Shop(id, type, owner, loc, villager, profession, name, suspended, rows, created, updated);
    }

    private static void setNullableString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, value);
    }

    private static String uuidStr(UUID id) { return id == null ? null : id.toString(); }

    private static String professionKey(Villager.Profession profession) {
        return profession == null ? null : profession.getKey().toString();
    }

    private static Villager.Profession parseProfession(String key) {
        if (key == null || key.isEmpty()) return null;
        NamespacedKey nk = NamespacedKey.fromString(key);
        if (nk == null) return null;
        return Registry.VILLAGER_PROFESSION.get(nk);
    }
}

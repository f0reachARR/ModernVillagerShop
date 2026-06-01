package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;

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

public final class MysqlCoOwnerRepository implements CoOwnerRepository {

    private final DataSource dataSource;

    public MysqlCoOwnerRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<CoOwner> findByShop(UUID shopId) throws SQLException {
        List<CoOwner> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shop_co_owners WHERE shop_id = ?")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public List<UUID> findShopsByPlayer(UUID playerUuid, CoOwnerRole role) throws SQLException {
        String sql = role == null
                ? "SELECT shop_id FROM shop_co_owners WHERE player_uuid = ?"
                : "SELECT shop_id FROM shop_co_owners WHERE player_uuid = ? AND role = ?";
        List<UUID> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            if (role != null) ps.setString(2, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(UUID.fromString(rs.getString(1)));
            }
        }
        return out;
    }

    @Override
    public void upsert(CoOwner owner) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO shop_co_owners (shop_id, player_uuid, role, share_pct, added_at, added_by) " +
                             "VALUES (?,?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE role=VALUES(role), share_pct=VALUES(share_pct)")) {
            ps.setString(1, owner.shopId().toString());
            ps.setString(2, owner.playerUuid().toString());
            ps.setString(3, owner.role().name());
            ps.setBigDecimal(4, owner.share());
            ps.setLong(5, owner.addedAt().toEpochMilli());
            if (owner.addedBy() == null) ps.setNull(6, Types.VARCHAR);
            else ps.setString(6, owner.addedBy().toString());
            ps.executeUpdate();
        }
    }

    @Override
    public void delete(UUID shopId, UUID playerUuid) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM shop_co_owners WHERE shop_id=? AND player_uuid=?")) {
            ps.setString(1, shopId.toString());
            ps.setString(2, playerUuid.toString());
            ps.executeUpdate();
        }
    }

    private static CoOwner map(ResultSet rs) throws SQLException {
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));
        CoOwnerRole role = CoOwnerRole.valueOf(rs.getString("role"));
        BigDecimal share = rs.getBigDecimal("share_pct");
        Instant addedAt = Instant.ofEpochMilli(rs.getLong("added_at"));
        String addedByStr = rs.getString("added_by");
        UUID addedBy = addedByStr == null ? null : UUID.fromString(addedByStr);
        return new CoOwner(shopId, playerId, role, share, addedAt, addedBy);
    }
}

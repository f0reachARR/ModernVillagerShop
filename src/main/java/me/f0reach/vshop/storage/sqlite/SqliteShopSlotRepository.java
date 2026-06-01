package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.item.ItemStackCodec;
import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.repo.ShopSlotRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqliteShopSlotRepository implements ShopSlotRepository {

    private final DataSource dataSource;

    public SqliteShopSlotRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<ShopSlot> findByShop(UUID shopId) throws SQLException {
        List<ShopSlot> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shop_slots WHERE shop_id = ? ORDER BY slot_index")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public Optional<ShopSlot> findById(UUID id) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shop_slots WHERE id = ?")) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public void upsert(ShopSlot slot) throws SQLException {
        // SQLite supports `INSERT ... ON CONFLICT(id) DO UPDATE`, but we keep an
        // explicit check to mirror the MySQL implementation's flow.
        try (Connection c = dataSource.getConnection()) {
            boolean exists;
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM shop_slots WHERE id = ?")) {
                ps.setString(1, slot.id().toString());
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (exists) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE shop_slots SET shop_id=?, slot_index=?, side=?, item_data=?, unit_price=?," +
                                "buy_unit_price=?, unit_amount=?, buy_capacity=?, trade_limit=?, limit_scope=?, reset_period_sec=? " +
                                "WHERE id=?")) {
                    bindBody(ps, slot, 1);
                    ps.setString(12, slot.id().toString());
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO shop_slots (id, shop_id, slot_index, side, item_data, unit_price," +
                                "buy_unit_price, unit_amount, buy_capacity, trade_limit, limit_scope, reset_period_sec) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, slot.id().toString());
                    bindBody(ps, slot, 2);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Override
    public void delete(UUID id) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM shop_limits WHERE slot_id = ?")) {
                    ps.setString(1, id.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM shop_slots WHERE id = ?")) {
                    ps.setString(1, id.toString());
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

    private static void bindBody(PreparedStatement ps, ShopSlot slot, int start) throws SQLException {
        int i = start;
        ps.setString(i++, slot.shopId().toString());
        ps.setInt(i++, slot.slotIndex());
        ps.setString(i++, slot.side().name());
        ps.setBytes(i++, ItemStackCodec.encode(slot.itemTemplate()));
        ps.setBigDecimal(i++, slot.unitPrice());
        if (slot.buyUnitPrice() == null) ps.setNull(i++, Types.DECIMAL);
        else ps.setBigDecimal(i++, slot.buyUnitPrice());
        ps.setInt(i++, slot.unitAmount());
        ps.setInt(i++, slot.buyCapacity());
        if (slot.tradeLimit() == null) ps.setNull(i++, Types.INTEGER);
        else ps.setInt(i++, slot.tradeLimit());
        ps.setString(i++, slot.limitScope().name());
        if (slot.resetPeriod() == null) ps.setNull(i, Types.BIGINT);
        else ps.setLong(i, slot.resetPeriod().getSeconds());
    }

    private static ShopSlot map(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        int slotIndex = rs.getInt("slot_index");
        TradeSide side = TradeSide.valueOf(rs.getString("side"));
        var item = ItemStackCodec.decode(rs.getBytes("item_data"));
        BigDecimal unitPrice = rs.getBigDecimal("unit_price");
        BigDecimal buyUnitPrice = rs.getBigDecimal("buy_unit_price");
        int unitAmount = rs.getInt("unit_amount");
        int buyCapacity = rs.getInt("buy_capacity");
        int limit = rs.getInt("trade_limit");
        Integer tradeLimit = rs.wasNull() ? null : limit;
        LimitScope scope = LimitScope.valueOf(rs.getString("limit_scope"));
        long resetSec = rs.getLong("reset_period_sec");
        Duration period = rs.wasNull() || resetSec <= 0 ? null : Duration.ofSeconds(resetSec);
        return new ShopSlot(id, shopId, slotIndex, side, item, unitPrice, buyUnitPrice,
                unitAmount, buyCapacity, tradeLimit, scope, period);
    }
}

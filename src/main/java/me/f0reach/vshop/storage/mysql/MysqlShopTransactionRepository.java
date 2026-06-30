package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.item.ItemStackCodec;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.repo.HistoryFilterSql;
import me.f0reach.vshop.storage.repo.ShopTransactionRepository;
import org.bukkit.inventory.ItemStack;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MysqlShopTransactionRepository implements ShopTransactionRepository {

    private final DataSource dataSource;

    public MysqlShopTransactionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public long insertTx(Connection c, TradeRecord rec) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO shop_transactions (occurred_at, shop_id, slot_id, side, buyer_uuid, seller_uuid, " +
                        "item_data, amount, unit_price, fee, base_price, final_price, resolved_by) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, rec.at().toEpochMilli());
            ps.setString(2, rec.shopId().toString());
            if (rec.slotId() == null) ps.setNull(3, Types.VARCHAR);
            else ps.setString(3, rec.slotId().toString());
            ps.setString(4, rec.side().name());
            if (rec.buyerUuid() == null) ps.setNull(5, Types.VARCHAR);
            else ps.setString(5, rec.buyerUuid().toString());
            if (rec.sellerUuid() == null) ps.setNull(6, Types.VARCHAR);
            else ps.setString(6, rec.sellerUuid().toString());
            ps.setBytes(7, ItemStackCodec.encode(rec.itemSnapshot()));
            ps.setInt(8, rec.amount());
            ps.setBigDecimal(9, rec.unitPrice());
            ps.setBigDecimal(10, rec.fee());
            if (rec.basePrice() == null) ps.setNull(11, Types.DECIMAL);
            else ps.setBigDecimal(11, rec.basePrice());
            if (rec.finalPrice() == null) ps.setNull(12, Types.DECIMAL);
            else ps.setBigDecimal(12, rec.finalPrice());
            if (rec.resolvedBy() == null) ps.setNull(13, Types.VARCHAR);
            else ps.setString(13, rec.resolvedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }

    @Override
    public List<TradeRecord> findByShop(UUID shopId, int limit, int offset) throws SQLException {
        List<TradeRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shop_transactions WHERE shop_id = ? ORDER BY occurred_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, shopId.toString());
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public List<TradeRecord> findByPlayer(UUID playerUuid, int limit, int offset) throws SQLException {
        List<TradeRecord> out = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shop_transactions WHERE buyer_uuid = ? OR seller_uuid = ? " +
                             "ORDER BY occurred_at DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, playerUuid.toString());
            ps.setInt(3, limit);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public List<TradeRecord> findFiltered(HistoryFilter filter, int limit, int offset) throws SQLException {
        List<TradeRecord> out = new ArrayList<>();
        String sql = "SELECT * FROM shop_transactions" + HistoryFilterSql.whereClause(filter)
                + " ORDER BY occurred_at DESC LIMIT ? OFFSET ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = HistoryFilterSql.bind(ps, 1, filter);
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    @Override
    public long countFiltered(HistoryFilter filter) throws SQLException {
        String sql = "SELECT COUNT(*) FROM shop_transactions" + HistoryFilterSql.whereClause(filter);
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            HistoryFilterSql.bind(ps, 1, filter);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    @Override
    public AggregateStats aggregate(UUID shopId) throws SQLException {
        long sellCount = 0, buyCount = 0;
        BigDecimal totalSell = BigDecimal.ZERO, totalBuy = BigDecimal.ZERO, totalFees = BigDecimal.ZERO;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT side, COUNT(*), COALESCE(SUM(unit_price * amount), 0), COALESCE(SUM(fee), 0) " +
                             "FROM shop_transactions WHERE shop_id = ? GROUP BY side")) {
            ps.setString(1, shopId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TradeSide side = TradeSide.valueOf(rs.getString(1));
                    long count = rs.getLong(2);
                    BigDecimal value = rs.getBigDecimal(3);
                    BigDecimal fee = rs.getBigDecimal(4);
                    if (side == TradeSide.SELL) { sellCount = count; totalSell = value; }
                    else if (side == TradeSide.BUY) { buyCount = count; totalBuy = value; }
                    totalFees = totalFees.add(fee);
                }
            }
        }
        return new AggregateStats(sellCount, buyCount, totalSell, totalBuy, totalFees);
    }

    @Override
    public List<DailyBucket> recentByDay(UUID shopId, int days, TradeSide side) throws SQLException {
        long since = Instant.now().minus(java.time.Duration.ofDays(days)).toEpochMilli();
        String sql = side == null
                ? "SELECT occurred_at, unit_price * amount FROM shop_transactions WHERE shop_id = ? AND occurred_at >= ?"
                : "SELECT occurred_at, unit_price * amount FROM shop_transactions WHERE shop_id = ? AND side = ? AND occurred_at >= ?";
        java.util.Map<Long, long[]> counts = new java.util.TreeMap<>();
        java.util.Map<Long, BigDecimal> values = new java.util.HashMap<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, shopId.toString());
            if (side != null) ps.setString(i++, side.name());
            ps.setLong(i, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long at = rs.getLong(1);
                    long bucket = (at / 86_400_000L) * 86_400_000L;
                    counts.computeIfAbsent(bucket, k -> new long[]{0})[0]++;
                    values.merge(bucket, rs.getBigDecimal(2), BigDecimal::add);
                }
            }
        }
        List<DailyBucket> out = new ArrayList<>();
        for (var e : counts.entrySet()) {
            out.add(new DailyBucket(Instant.ofEpochMilli(e.getKey()),
                    e.getValue()[0], values.getOrDefault(e.getKey(), BigDecimal.ZERO)));
        }
        return out;
    }

    @Override
    public SlotAggregate slotAggregate(UUID shopId, UUID slotId, TradeSide side, Instant from, Instant to)
            throws SQLException {
        StringBuilder sb = new StringBuilder(
                "SELECT COUNT(*), COALESCE(SUM(amount),0), COALESCE(SUM(unit_price * amount),0) " +
                        "FROM shop_transactions WHERE shop_id = ? AND slot_id = ?");
        if (side != null) sb.append(" AND side = ?");
        if (from != null) sb.append(" AND occurred_at > ?");
        if (to != null) sb.append(" AND occurred_at <= ?");
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int i = 1;
            ps.setString(i++, shopId.toString());
            ps.setString(i++, slotId.toString());
            if (side != null) ps.setString(i++, side.name());
            if (from != null) ps.setLong(i++, from.toEpochMilli());
            if (to != null) ps.setLong(i, to.toEpochMilli());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new SlotAggregate(0L, 0L, BigDecimal.ZERO);
                return new SlotAggregate(rs.getLong(1), rs.getLong(2),
                        rs.getBigDecimal(3) != null ? rs.getBigDecimal(3) : BigDecimal.ZERO);
            }
        }
    }

    private static TradeRecord map(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        Instant at = Instant.ofEpochMilli(rs.getLong("occurred_at"));
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        String slotStr = rs.getString("slot_id");
        UUID slotId = slotStr == null ? null : UUID.fromString(slotStr);
        TradeSide side = TradeSide.valueOf(rs.getString("side"));
        String buyer = rs.getString("buyer_uuid");
        String seller = rs.getString("seller_uuid");
        UUID buyerUuid = buyer == null ? null : UUID.fromString(buyer);
        UUID sellerUuid = seller == null ? null : UUID.fromString(seller);
        ItemStack item = ItemStackCodec.decode(rs.getBytes("item_data"));
        int amount = rs.getInt("amount");
        BigDecimal unit = rs.getBigDecimal("unit_price");
        BigDecimal fee = rs.getBigDecimal("fee");
        BigDecimal base = rs.getBigDecimal("base_price");
        BigDecimal fin = rs.getBigDecimal("final_price");
        String resolvedBy = rs.getString("resolved_by");
        return new TradeRecord(id, at, shopId, slotId, side, buyerUuid, sellerUuid,
                item, amount, unit, fee, base, fin, resolvedBy);
    }
}

package me.f0reach.vshop.storage.repo;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the dynamic WHERE clause used by both SQLite and MySQL implementations
 * of {@link ShopTransactionRepository#findFiltered}. Kept here so the two
 * dialects share the same column references and parameter ordering.
 */
public final class HistoryFilterSql {

    private HistoryFilterSql() {}

    public static String whereClause(ShopTransactionRepository.HistoryFilter f) {
        List<String> parts = new ArrayList<>();
        if (f.shopId() != null) parts.add("shop_id = ?");
        if (f.playerUuid() != null) parts.add("(buyer_uuid = ? OR seller_uuid = ?)");
        if (f.side() != null) parts.add("side = ?");
        if (f.from() != null) parts.add("occurred_at >= ?");
        if (f.to() != null) parts.add("occurred_at < ?");
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    /** Binds parameters in the same order as {@link #whereClause}. Returns next index. */
    public static int bind(PreparedStatement ps, int startIndex, ShopTransactionRepository.HistoryFilter f)
            throws SQLException {
        int i = startIndex;
        if (f.shopId() != null) ps.setString(i++, f.shopId().toString());
        if (f.playerUuid() != null) {
            String s = f.playerUuid().toString();
            ps.setString(i++, s);
            ps.setString(i++, s);
        }
        if (f.side() != null) ps.setString(i++, f.side().name());
        if (f.from() != null) ps.setLong(i++, f.from().toEpochMilli());
        if (f.to() != null) ps.setLong(i++, f.to().toEpochMilli());
        return i;
    }
}

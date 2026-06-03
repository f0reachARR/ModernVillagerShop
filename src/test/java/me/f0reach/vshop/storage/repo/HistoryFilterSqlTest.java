package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.TradeSide;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the dialect-shared {@link HistoryFilterSql} helper. Verifies
 * the WHERE clause shape and that the bind index advances by the right amount
 * for every supported combination of filter fields.
 */
class HistoryFilterSqlTest {

    @Test
    void emptyFilterEmitsNoWhereClause() {
        var filter = new ShopTransactionRepository.HistoryFilter(null, null, null, null, null);
        assertEquals("", HistoryFilterSql.whereClause(filter));
    }

    @Test
    void shopOnlyEmitsSingleClause() {
        var filter = new ShopTransactionRepository.HistoryFilter(UUID.randomUUID(), null, null, null, null);
        assertEquals(" WHERE shop_id = ?", HistoryFilterSql.whereClause(filter));
    }

    @Test
    void playerEmitsBuyerOrSellerClause() {
        var filter = ShopTransactionRepository.HistoryFilter.forPlayer(UUID.randomUUID());
        assertEquals(" WHERE (buyer_uuid = ? OR seller_uuid = ?)", HistoryFilterSql.whereClause(filter));
    }

    @Test
    void allFieldsEmitFullClauseInOrder() {
        var filter = new ShopTransactionRepository.HistoryFilter(
                UUID.randomUUID(), UUID.randomUUID(), TradeSide.SELL,
                Instant.ofEpochMilli(1), Instant.ofEpochMilli(2));
        String where = HistoryFilterSql.whereClause(filter);
        // shop_id, buyer/seller, side, from, to — in this exact order.
        assertTrue(where.startsWith(" WHERE shop_id = ? AND (buyer_uuid = ? OR seller_uuid = ?)"));
        assertTrue(where.contains("side = ?"));
        assertTrue(where.contains("occurred_at >= ?"));
        assertTrue(where.endsWith("occurred_at < ?"));
    }

    @Test
    void bindAdvancesIndexByOneClausePerField() throws SQLException {
        // bind() only calls setString/setLong on the PreparedStatement — a JDK
        // proxy that no-ops on every method is enough to satisfy the contract.
        PreparedStatement ps = noopPreparedStatement();

        UUID shop = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        var filter = new ShopTransactionRepository.HistoryFilter(
                shop, player, TradeSide.BUY,
                Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000));

        int next = HistoryFilterSql.bind(ps, 1, filter);
        // shop(1) + player×2 (2,3) + side(4) + from(5) + to(6) → next = 7.
        assertEquals(7, next);
    }

    @Test
    void bindOnEmptyFilterDoesNotAdvanceIndex() throws SQLException {
        PreparedStatement ps = noopPreparedStatement();
        var filter = new ShopTransactionRepository.HistoryFilter(null, null, null, null, null);
        assertEquals(5, HistoryFilterSql.bind(ps, 5, filter));
    }

    private static PreparedStatement noopPreparedStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
                HistoryFilterSqlTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> null);
    }
}

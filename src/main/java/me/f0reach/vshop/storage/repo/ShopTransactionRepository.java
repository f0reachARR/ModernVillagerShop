package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.model.TradeSide;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShopTransactionRepository {

    long insertTx(Connection c, TradeRecord rec) throws SQLException;

    List<TradeRecord> findByShop(UUID shopId, int limit, int offset) throws SQLException;

    List<TradeRecord> findByPlayer(UUID playerUuid, int limit, int offset) throws SQLException;

    List<TradeRecord> findFiltered(HistoryFilter filter, int limit, int offset) throws SQLException;

    long countFiltered(HistoryFilter filter) throws SQLException;

    /** Aggregated counts by side for a shop. */
    AggregateStats aggregate(UUID shopId) throws SQLException;

    /** Recent activity (last `days` days), bucketed by day epoch. */
    List<DailyBucket> recentByDay(UUID shopId, int days, TradeSide side) throws SQLException;

    record AggregateStats(
            long sellCount,
            long buyCount,
            java.math.BigDecimal totalSalesValue,
            java.math.BigDecimal totalBuyValue,
            java.math.BigDecimal totalFees
    ) {}

    record DailyBucket(Instant dayStart, long count, java.math.BigDecimal value) {}

    /**
     * Optional filters for {@link #findFiltered}. A null field means no constraint.
     * playerUuid matches either buyer or seller.
     */
    record HistoryFilter(
            UUID shopId,
            UUID playerUuid,
            TradeSide side,
            Instant from,
            Instant to
    ) {
        public static HistoryFilter forPlayer(UUID playerUuid) {
            return new HistoryFilter(null, playerUuid, null, null, null);
        }
    }
}

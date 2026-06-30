package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.api.price.TransactionHistoryView;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.StorageManager;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQL-backed {@link TransactionHistoryView} bound to one {@code (shopId, slotId)} pair.
 * Each accessor performs a fresh aggregate query — providers must cache via
 * {@link me.f0reach.vshop.api.price.PriceResult#ttl()} per spec §12.3.3.
 */
public final class DbTransactionHistoryView implements TransactionHistoryView {

    private static final Logger LOG = Logger.getLogger(DbTransactionHistoryView.class.getName());

    private final StorageManager storage;
    private final UUID shopId;
    private final UUID slotId;

    public DbTransactionHistoryView(StorageManager storage, UUID shopId, UUID slotId) {
        this.storage = storage;
        this.shopId = shopId;
        this.slotId = slotId;
    }

    @Override
    public long totalAmount(@Nullable TradeSide side, @Nullable Instant from, @Nullable Instant to) {
        return aggregate(side, from, to).totalAmount();
    }

    @Override
    public BigDecimal totalValue(@Nullable TradeSide side, @Nullable Instant from, @Nullable Instant to) {
        return aggregate(side, from, to).totalValue();
    }

    @Override
    public long count(@Nullable TradeSide side, @Nullable Instant from, @Nullable Instant to) {
        return aggregate(side, from, to).count();
    }

    private me.f0reach.vshop.storage.repo.ShopTransactionRepository.SlotAggregate aggregate(
            TradeSide side, Instant from, Instant to) {
        try {
            return storage.transactions().slotAggregate(shopId, slotId, side, from, to);
        } catch (SQLException ex) {
            LOG.warning("slotAggregate failed for shop=" + shopId + " slot=" + slotId + ": " + ex.getMessage());
            return new me.f0reach.vshop.storage.repo.ShopTransactionRepository.SlotAggregate(
                    0L, 0L, BigDecimal.ZERO);
        }
    }
}

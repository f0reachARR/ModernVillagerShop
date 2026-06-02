package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Snapshot of a confirmed trade attempt. {@code unitPriceSnapshot} captures
 * the price shown in the confirm dialog so the trade can be cancelled if the
 * live-resolved price drifts past {@code economy.priceDriftTolerance} between
 * confirm and settlement (spec §12.3.2).
 *
 * {@code basePrice} and {@code resolvedBy} record the price-pipeline state at
 * snapshot time, so the persisted {@link me.f0reach.vshop.model.TradeRecord}
 * can include the same fields without a second resolve at insert time.
 */
public record TradeRequest(
        Player viewer,
        Shop shop,
        ShopSlot slot,
        TradeSide side,        // SELL or BUY (BOTH gets resolved before this point)
        int packCount,         // count of "unitAmount-sized" packs to trade
        BigDecimal unitPriceSnapshot,
        BigDecimal basePrice,
        @Nullable String resolvedBy
) {

    /** Compat constructor for callsites/tests that don't yet plumb the pipeline metadata. */
    public TradeRequest(Player viewer, Shop shop, ShopSlot slot, TradeSide side,
                        int packCount, BigDecimal unitPriceSnapshot) {
        this(viewer, shop, slot, side, packCount, unitPriceSnapshot, unitPriceSnapshot, null);
    }

    public int totalItems() { return packCount * slot.unitAmount(); }
    public BigDecimal gross() { return unitPriceSnapshot.multiply(BigDecimal.valueOf(packCount)); }
}

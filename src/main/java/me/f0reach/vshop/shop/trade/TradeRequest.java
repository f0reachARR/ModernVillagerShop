package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;

import java.math.BigDecimal;
import org.bukkit.entity.Player;

/**
 * Snapshot of a confirmed trade attempt. The {@code unitPriceSnapshot} captures
 * the price shown in the confirm dialog so the trade can be cancelled if the
 * live-resolved price has drifted past tolerance.
 */
public record TradeRequest(
        Player viewer,
        Shop shop,
        ShopSlot slot,
        TradeSide side,        // SELL or BUY (BOTH gets resolved before this point)
        int packCount,         // count of "unitAmount-sized" packs to trade
        BigDecimal unitPriceSnapshot
) {

    public int totalItems() { return packCount * slot.unitAmount(); }
    public BigDecimal gross() { return unitPriceSnapshot.multiply(BigDecimal.valueOf(packCount)); }
}

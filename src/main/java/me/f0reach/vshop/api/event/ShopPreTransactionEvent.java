package me.f0reach.vshop.api.event;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Fired immediately before a trade is settled. Cancelling refuses the trade
 * (with no funds or items moved). Price/amount mutation is intentionally NOT
 * supported here — price comes from {@link me.f0reach.vshop.api.price.PriceProvider}.
 */
public final class ShopPreTransactionEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Shop shop;
    private final ShopSlot slot;
    private final TradeSide side;
    private final Player player;
    private final int amount;
    private final BigDecimal unitPrice;
    private boolean cancelled;
    private String cancelReason;

    public ShopPreTransactionEvent(@NotNull Shop shop, @NotNull ShopSlot slot, @NotNull TradeSide side,
                                   @NotNull Player player, int amount, @NotNull BigDecimal unitPrice) {
        this.shop = shop;
        this.slot = slot;
        this.side = side;
        this.player = player;
        this.amount = amount;
        this.unitPrice = unitPrice;
    }

    public Shop shop() { return shop; }
    public ShopSlot slot() { return slot; }
    public TradeSide side() { return side; }
    public Player player() { return player; }
    public int amount() { return amount; }
    public BigDecimal unitPrice() { return unitPrice; }
    public String cancelReason() { return cancelReason; }
    public void setCancelReason(String reason) { this.cancelReason = reason; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

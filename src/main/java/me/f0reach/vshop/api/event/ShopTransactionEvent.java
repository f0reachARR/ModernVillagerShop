package me.f0reach.vshop.api.event;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ShopTransactionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Shop shop;
    private final TradeRecord record;

    public ShopTransactionEvent(@NotNull Shop shop, @NotNull TradeRecord record) {
        this.shop = shop;
        this.record = record;
    }

    public Shop shop() { return shop; }
    public TradeRecord record() { return record; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

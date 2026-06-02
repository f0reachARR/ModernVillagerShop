package me.f0reach.vshop.api.event;

import me.f0reach.vshop.model.Shop;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ShopDeleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Shop shop;

    public ShopDeleteEvent(@NotNull Shop shop) { this.shop = shop; }

    public Shop shop() { return shop; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

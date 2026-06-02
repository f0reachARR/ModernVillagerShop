package me.f0reach.vshop.api.event;

import me.f0reach.vshop.model.Shop;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ShopCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Shop shop;
    private final Player creator;

    public ShopCreateEvent(@NotNull Shop shop, @Nullable Player creator) {
        this.shop = shop;
        this.creator = creator;
    }

    public Shop shop() { return shop; }
    public @Nullable Player creator() { return creator; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

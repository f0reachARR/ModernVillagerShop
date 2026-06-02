package me.f0reach.vshop.api.event;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public final class ShopSlotChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Kind { CREATED, UPDATED, DELETED }

    private final Shop shop;
    private final ShopSlot slot;
    private final Kind kind;

    public ShopSlotChangeEvent(@NotNull Shop shop, @NotNull ShopSlot slot, @NotNull Kind kind) {
        this.shop = shop;
        this.slot = slot;
        this.kind = kind;
    }

    public Shop shop() { return shop; }
    public ShopSlot slot() { return slot; }
    public Kind kind() { return kind; }

    @NotNull @Override public HandlerList getHandlers() { return HANDLERS; }
    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}

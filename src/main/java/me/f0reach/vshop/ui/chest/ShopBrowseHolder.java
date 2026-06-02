package me.f0reach.vshop.ui.chest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker {@link InventoryHolder} for the read-only shop browse view. The
 * holder doubles as the per-session state container — it remembers the player,
 * current page and the shop being viewed so the click listener can act on it.
 */
public final class ShopBrowseHolder implements InventoryHolder {

    private final UUID viewerId;
    private final UUID shopId;
    private int page;
    private Inventory inventory;

    public ShopBrowseHolder(Player viewer, UUID shopId, int page) {
        this.viewerId = viewer.getUniqueId();
        this.shopId = shopId;
        this.page = page;
    }

    public Inventory createInventory(int size, net.kyori.adventure.text.Component title) {
        // A chest inventory must have a slot count divisible by 9, max 54.
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() { return viewerId; }
    public UUID shopId() { return shopId; }
    public int page() { return page; }
    public void setPage(int page) { this.page = page; }
}

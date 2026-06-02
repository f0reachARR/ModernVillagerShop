package me.f0reach.vshop.ui.chest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder for the edit-mode chest view. Tracks the editor, target shop
 * and current page so the click router can find them.
 */
public final class ShopEditHolder implements InventoryHolder {

    private final UUID editorId;
    private final UUID shopId;
    private int page;
    private Inventory inventory;

    public ShopEditHolder(Player editor, UUID shopId, int page) {
        this.editorId = editor.getUniqueId();
        this.shopId = shopId;
        this.page = page;
    }

    public Inventory createInventory(int size, net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public UUID editorId() { return editorId; }
    public UUID shopId() { return shopId; }
    public int page() { return page; }
    public void setPage(int page) { this.page = page; }
}

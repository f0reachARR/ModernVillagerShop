package me.f0reach.vshop.ui.inventory.base;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public abstract class BaseInventoryUI {
    protected final Player viewer;
    protected final ShopInventoryHolder holder;
    protected Inventory inventory;

    protected BaseInventoryUI(Player viewer, int size, Component title) {
        this.viewer = viewer;
        this.holder = new ShopInventoryHolder(this);
        this.inventory = Bukkit.createInventory(holder, size, title);
        this.holder.setInventory(inventory);
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    public void close() {
        viewer.closeInventory();
    }

    protected abstract void render();

    public abstract void handleClick(InventoryClickEvent event);

    public void handleClose() {
        // Override if needed
    }

    public Player getViewer() {
        return viewer;
    }

    public Inventory getInventory() {
        return inventory;
    }
}

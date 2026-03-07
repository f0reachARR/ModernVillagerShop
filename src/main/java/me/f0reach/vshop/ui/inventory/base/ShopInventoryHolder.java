package me.f0reach.vshop.ui.inventory.base;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class ShopInventoryHolder implements InventoryHolder {
    private final BaseInventoryUI ui;
    private Inventory inventory;

    public ShopInventoryHolder(BaseInventoryUI ui) {
        this.ui = ui;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public BaseInventoryUI getUI() {
        return ui;
    }
}

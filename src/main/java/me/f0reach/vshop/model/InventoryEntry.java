package me.f0reach.vshop.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * A single slot in a player-shop dedicated inventory.
 */
public final class InventoryEntry {

    private final UUID shopId;
    private final int slotIndex;
    private ItemStack item;
    private int amount;

    public InventoryEntry(UUID shopId, int slotIndex, ItemStack item, int amount) {
        this.shopId = shopId;
        this.slotIndex = slotIndex;
        this.item = item;
        this.amount = amount;
    }

    public UUID shopId() { return shopId; }
    public int slotIndex() { return slotIndex; }
    public ItemStack item() { return item; }
    public int amount() { return amount; }

    public void setItem(ItemStack item) { this.item = item; }
    public void setAmount(int amount) { this.amount = amount; }
}

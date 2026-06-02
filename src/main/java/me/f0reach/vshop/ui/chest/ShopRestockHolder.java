package me.f0reach.vshop.ui.chest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-session state for the restock UI. Holds a snapshot of the original
 * shop_inventory rows so the close handler can detect which chest slots
 * were edited and which can be left at their possibly-overflowing real
 * amount (since a chest slot can only display up to {@code maxStackSize}).
 */
public final class ShopRestockHolder implements InventoryHolder {

    private final UUID viewerId;
    private final UUID shopId;
    private final int slotCount;
    private final Map<Integer, SnapshotEntry> snapshot = new HashMap<>();
    private Inventory inventory;
    private boolean saved;

    public ShopRestockHolder(Player viewer, UUID shopId, int slotCount) {
        this.viewerId = viewer.getUniqueId();
        this.shopId = shopId;
        this.slotCount = slotCount;
    }

    public Inventory createInventory(int size, net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    public void snapshotPut(int slotIndex, ItemStack template, int realAmount, int displayedAmount) {
        snapshot.put(slotIndex, new SnapshotEntry(template, realAmount, displayedAmount));
    }

    public SnapshotEntry snapshotOf(int slotIndex) { return snapshot.get(slotIndex); }

    @Override public Inventory getInventory() { return inventory; }
    public UUID viewerId() { return viewerId; }
    public UUID shopId() { return shopId; }
    public int slotCount() { return slotCount; }
    public boolean saved() { return saved; }
    public void markSaved() { this.saved = true; }

    /** {@code template} is a size-1 template; {@code realAmount} is the DB value (may exceed 64). */
    public record SnapshotEntry(ItemStack template, int realAmount, int displayedAmount) {}
}

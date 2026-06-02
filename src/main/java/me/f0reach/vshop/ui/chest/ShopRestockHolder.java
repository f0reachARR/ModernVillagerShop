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
 * Per-session state for the restock UI. The chest is paginated so the shop's
 * storage capacity is unbounded: page {@code p} maps chest slot {@code i}
 * to {@code shop_inventory.slot_index = p * PAGE_SIZE + i}.
 *
 * The snapshot covers the CURRENT page only — it's reloaded whenever the
 * player flips pages so the close/save diff knows which slots are unchanged
 * vs. edited.
 */
public final class ShopRestockHolder implements InventoryHolder {

    /** Content slots per page (5 rows). The 6th row is reserved for nav. */
    public static final int PAGE_SIZE = 45;
    public static final int INVENTORY_SIZE = 54;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_PAGE_INDICATOR = 49;
    public static final int SLOT_CLOSE = 51;
    public static final int SLOT_NEXT = 53;

    private final UUID viewerId;
    private final UUID shopId;
    private int page;
    private final Map<Integer, SnapshotEntry> snapshot = new HashMap<>();
    private Inventory inventory;
    private boolean saved;
    private boolean suppressCloseSave;

    public ShopRestockHolder(Player viewer, UUID shopId) {
        this.viewerId = viewer.getUniqueId();
        this.shopId = shopId;
    }

    public Inventory createInventory(int size, net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    public void snapshotClear() { snapshot.clear(); }

    public void snapshotPut(int chestSlot, ItemStack template, int realAmount, int displayedAmount) {
        snapshot.put(chestSlot, new SnapshotEntry(template, realAmount, displayedAmount));
    }

    public SnapshotEntry snapshotOf(int chestSlot) { return snapshot.get(chestSlot); }

    @Override public Inventory getInventory() { return inventory; }
    public UUID viewerId() { return viewerId; }
    public UUID shopId() { return shopId; }
    public int page() { return page; }
    public void setPage(int page) { this.page = page; }
    public boolean saved() { return saved; }
    public void markSaved() { this.saved = true; }
    public boolean suppressCloseSave() { return suppressCloseSave; }
    public void setSuppressCloseSave(boolean suppress) { this.suppressCloseSave = suppress; }

    /** Maps a chest slot in the current page to its global {@code shop_inventory.slot_index}. */
    public int toGlobalSlot(int chestSlot) { return page * PAGE_SIZE + chestSlot; }

    /** {@code template} is a size-1 template; {@code realAmount} is the DB value (may exceed 64). */
    public record SnapshotEntry(ItemStack template, int realAmount, int displayedAmount) {}
}

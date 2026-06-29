package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-session state for the restock UI. Chest dimensions follow the shop's
 * {@code rowCount}: finite shops (1-6) get a single page sized to rowCount
 * rows with no nav row; infinite shops paginate with 45 slots per page and
 * map chest slot {@code i} on page {@code p} to {@code shop_inventory.slot_index = p * 45 + i}.
 *
 * The snapshot covers the CURRENT page only — it's reloaded whenever the
 * player flips pages so the close/save diff knows which slots are unchanged
 * vs. edited.
 */
public final class ShopRestockHolder implements InventoryHolder {

    private final UUID viewerId;
    private final UUID shopId;
    private final int contentSlots;
    private final int inventorySize;
    private final boolean paginated;
    private final int slotIndexLimit;
    private final int slotPrev;
    private final int slotPageIndicator;
    private final int slotClose;
    private final int slotNext;
    private int page;
    private final Map<Integer, SnapshotEntry> snapshot = new HashMap<>();
    private Inventory inventory;
    private boolean saved;
    private boolean suppressCloseSave;

    public ShopRestockHolder(Player viewer, Shop shop) {
        this.viewerId = viewer.getUniqueId();
        this.shopId = shop.id();
        this.contentSlots = shop.chestContentSlots();
        this.inventorySize = shop.chestInventorySize();
        this.paginated = shop.isPaginated();
        this.slotIndexLimit = shop.slotIndexLimit();
        if (paginated) {
            int navStart = contentSlots;
            this.slotPrev = navStart;
            this.slotPageIndicator = navStart + 4;
            this.slotClose = navStart + 6;
            this.slotNext = navStart + 8;
        } else {
            this.slotPrev = -1;
            this.slotPageIndicator = -1;
            this.slotClose = -1;
            this.slotNext = -1;
        }
    }

    public Inventory createInventory(net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, inventorySize, title);
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

    public int contentSlots() { return contentSlots; }
    public int inventorySize() { return inventorySize; }
    public boolean paginated() { return paginated; }
    public int slotPrev() { return slotPrev; }
    public int slotPageIndicator() { return slotPageIndicator; }
    public int slotClose() { return slotClose; }
    public int slotNext() { return slotNext; }

    /** Maps a chest slot in the current page to its global {@code shop_inventory.slot_index}. */
    public int toGlobalSlot(int chestSlot) { return page * contentSlots + chestSlot; }

    /** True when {@code chestSlot} maps to a valid global slot_index for this page. */
    public boolean isContentSlotInBounds(int chestSlot) {
        return toGlobalSlot(chestSlot) < slotIndexLimit;
    }

    /** {@code template} is a size-1 template; {@code realAmount} is the DB value (may exceed 64). */
    public record SnapshotEntry(ItemStack template, int realAmount, int displayedAmount) {}
}

package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker {@link InventoryHolder} for the read-only shop browse view. The
 * holder doubles as the per-session state container — it remembers the player,
 * current page and the shop being viewed so the click listener can act on it.
 *
 * Chest dimensions are derived from the shop's {@code rowCount}: finite shops
 * (1-6 rows) get a single-page chest sized to {@code rowCount * 9} with no
 * nav row; infinite shops get the classic 5-content + 1-nav 54-slot chest.
 */
public final class ShopBrowseHolder implements InventoryHolder {

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
    private Inventory inventory;

    public ShopBrowseHolder(Player viewer, Shop shop, int page) {
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
            this.slotClose = navStart + 5;
            this.slotNext = navStart + 8;
        } else {
            this.slotPrev = -1;
            this.slotPageIndicator = -1;
            this.slotClose = -1;
            this.slotNext = -1;
        }
        this.page = page;
    }

    public Inventory createInventory(net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, inventorySize, title);
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

    public int contentSlots() { return contentSlots; }
    public int inventorySize() { return inventorySize; }
    public boolean paginated() { return paginated; }
    public int slotPrev() { return slotPrev; }
    public int slotPageIndicator() { return slotPageIndicator; }
    public int slotClose() { return slotClose; }
    public int slotNext() { return slotNext; }

    /** True when {@code chestSlot} maps to a valid global slot_index for this page. */
    public boolean isContentSlotInBounds(int chestSlot) {
        return page * contentSlots + chestSlot < slotIndexLimit;
    }
}

package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder for the edit-mode chest view. Tracks the editor, target shop
 * and current page so the click router can find them. Chest dimensions follow
 * the shop's {@code rowCount} (see {@link Shop#chestInventorySize()}).
 */
public final class ShopEditHolder implements InventoryHolder {

    private final UUID editorId;
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
    private final Runnable onClose;
    private boolean suppressReturnOnClose;

    public ShopEditHolder(Player editor, Shop shop, int page) {
        this(editor, shop, page, null);
    }

    public ShopEditHolder(Player editor, Shop shop, int page, Runnable onClose) {
        this.editorId = editor.getUniqueId();
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
        this.onClose = onClose;
    }

    public Inventory createInventory(net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, inventorySize, title);
        return this.inventory;
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public UUID editorId() { return editorId; }
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

    public Runnable onClose() { return onClose; }
    public boolean suppressReturnOnClose() { return suppressReturnOnClose; }
    public void setSuppressReturnOnClose(boolean suppress) { this.suppressReturnOnClose = suppress; }

    /** True when {@code chestSlot} maps to a valid global slot_index for this page. */
    public boolean isContentSlotInBounds(int chestSlot) {
        return page * contentSlots + chestSlot < slotIndexLimit;
    }
}

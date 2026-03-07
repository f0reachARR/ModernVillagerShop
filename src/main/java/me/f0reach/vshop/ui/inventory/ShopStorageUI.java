package me.f0reach.vshop.ui.inventory;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import me.f0reach.vshop.ui.inventory.base.BaseInventoryUI;
import me.f0reach.vshop.ui.inventory.item.NavigationItems;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;

public final class ShopStorageUI extends BaseInventoryUI {
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int CONTENT_SLOTS = 45;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final Shop shop;
    private final MessageManager messages;
    private final UIManager uiManager;
    private int currentPage;

    public ShopStorageUI(Player viewer, Shop shop, MessageManager messages, UIManager uiManager) {
        super(viewer, SIZE, messages.get("shop.storage_title",
                Placeholder.unparsed("shop_id", String.valueOf(shop.shopId()))));
        this.shop = shop;
        this.messages = messages;
        this.uiManager = uiManager;
    }

    @Override
    protected void render() {
        inventory.clear();

        Map<Integer, ItemStack> contents = getContents();
        int maxUsedSlot = contents.keySet().stream()
                .filter(slot -> slot >= 0)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1);
        int maxDataPage = maxUsedSlot < 0 ? 0 : maxUsedSlot / CONTENT_SLOTS;
        int maxPage = maxDataPage + 1; // Always allow one trailing empty page

        if (currentPage > maxPage) {
            currentPage = maxPage;
        }

        for (Map.Entry<Integer, ItemStack> entry : contents.entrySet()) {
            int absoluteSlot = entry.getKey();
            if (absoluteSlot < 0 || absoluteSlot / CONTENT_SLOTS != currentPage) {
                continue;
            }
            inventory.setItem(absoluteSlot % CONTENT_SLOTS, entry.getValue().clone());
        }

        for (int slot = 45; slot < SIZE; slot++) {
            inventory.setItem(slot, NavigationItems.filler());
        }
        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, NavigationItems.prevPage(messages));
        }
        inventory.setItem(SLOT_BACK, NavigationItems.backToListings(messages));
        inventory.setItem(SLOT_PAGE_INFO, NavigationItems.pageInfo(messages, currentPage + 1, maxPage + 1));
        if (currentPage < maxPage) {
            inventory.setItem(SLOT_NEXT, NavigationItems.nextPage(messages));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }

        if (rawSlot < SIZE) {
            event.setCancelled(true);
            if (rawSlot < CONTENT_SLOTS) {
                handleStorageSlotClick(event, rawSlot);
            } else {
                handleNavClick(rawSlot);
            }
            return;
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                event.setCancelled(true);
                shiftDepositFromPlayerInventory(event.getClickedInventory(), event.getSlot(), current);
            }
        } else {
            event.setCancelled(false);
        }
    }

    private void handleNavClick(int slot) {
        switch (slot) {
            case SLOT_PREV -> {
                if (currentPage > 0) {
                    currentPage--;
                    render();
                }
            }
            case SLOT_NEXT -> {
                currentPage++;
                render();
            }
            case SLOT_BACK -> uiManager.openShopInventory(viewer, shop);
        }
    }

    private void handleStorageSlotClick(InventoryClickEvent event, int displaySlot) {
        int absoluteSlot = toAbsoluteSlot(displaySlot);
        ItemStack slotItem = inventory.getItem(displaySlot);

        if (event.isShiftClick()) {
            shiftWithdrawToPlayer(absoluteSlot, slotItem);
            return;
        }

        ItemStack cursor = event.getCursor();

        try {
            if (isAir(cursor)) {
                if (isAir(slotItem)) {
                    return;
                }
                viewer.setItemOnCursor(slotItem.clone());
                uiManager.getShopService().setShopInventorySlot(shop.shopId(), absoluteSlot, null);
                render();
                return;
            }

            if (isAir(slotItem)) {
                int moveAmount = event.isRightClick() ? 1 : cursor.getAmount();
                int placeAmount = Math.min(moveAmount, cursor.getMaxStackSize());
                if (placeAmount <= 0) {
                    return;
                }

                ItemStack placed = cursor.clone();
                placed.setAmount(placeAmount);
                uiManager.getShopService().setShopInventorySlot(shop.shopId(), absoluteSlot, placed);
                decreaseCursor(placeAmount);
                render();
                return;
            }

            if (slotItem.isSimilar(cursor)) {
                int space = slotItem.getMaxStackSize() - slotItem.getAmount();
                if (space <= 0) {
                    return;
                }

                int moveAmount = event.isRightClick() ? 1 : cursor.getAmount();
                int add = Math.min(space, moveAmount);
                if (add <= 0) {
                    return;
                }
                slotItem.setAmount(slotItem.getAmount() + add);
                uiManager.getShopService().setShopInventorySlot(shop.shopId(), absoluteSlot, slotItem);
                decreaseCursor(add);
                render();
                return;
            }

            if (!event.isRightClick()) {
                uiManager.getShopService().setShopInventorySlot(shop.shopId(), absoluteSlot, cursor.clone());
                viewer.setItemOnCursor(slotItem.clone());
                render();
            }
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to update shop storage slot", e);
            viewer.sendMessage(messages.get("error.storage"));
        }
    }

    private void shiftWithdrawToPlayer(int absoluteSlot, ItemStack slotItem) {
        if (isAir(slotItem)) {
            return;
        }
        Map<Integer, ItemStack> leftovers = viewer.getInventory().addItem(slotItem.clone());
        ItemStack remaining = leftovers.isEmpty() ? null : leftovers.values().iterator().next();

        try {
            uiManager.getShopService().setShopInventorySlot(shop.shopId(), absoluteSlot, remaining);
            render();
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to withdraw from shop storage", e);
            viewer.sendMessage(messages.get("error.storage"));
        }
    }

    private void shiftDepositFromPlayerInventory(Inventory clickedInventory, int slot, ItemStack current) {
        try {
            ItemStack leftover = uiManager.getShopService().addItemToShopInventory(shop.shopId(), current.clone());
            clickedInventory.setItem(slot, leftover);
            render();
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to deposit to shop storage", e);
            viewer.sendMessage(messages.get("error.storage"));
        }
    }

    private Map<Integer, ItemStack> getContents() {
        try {
            return uiManager.getShopService().getShopInventoryContents(shop.shopId());
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to load shop storage", e);
            return Collections.emptyMap();
        }
    }

    private int toAbsoluteSlot(int contentSlot) {
        return currentPage * CONTENT_SLOTS + contentSlot;
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private void decreaseCursor(int amount) {
        ItemStack cursor = viewer.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        int remaining = cursor.getAmount() - amount;
        if (remaining <= 0) {
            viewer.setItemOnCursor(null);
        } else {
            cursor.setAmount(remaining);
            viewer.setItemOnCursor(cursor);
        }
    }
}

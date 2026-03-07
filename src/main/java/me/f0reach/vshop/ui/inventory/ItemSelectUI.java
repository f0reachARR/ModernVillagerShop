package me.f0reach.vshop.ui.inventory;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import me.f0reach.vshop.ui.inventory.base.BaseInventoryUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ItemSelectUI extends BaseInventoryUI {
    private static final int SIZE = 27;
    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_ITEM = 13;
    private static final int SLOT_CANCEL = 15;

    private final Shop shop;
    private final MessageManager messages;
    private final UIManager uiManager;
    private ItemStack selectedItem;

    public ItemSelectUI(Player viewer, Shop shop, MessageManager messages, UIManager uiManager) {
        super(viewer, SIZE, messages.get("dialog.item_select_title"));
        this.shop = shop;
        this.messages = messages;
        this.uiManager = uiManager;
    }

    @Override
    protected void render() {
        ItemStack filler = createFiller();
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        inventory.setItem(SLOT_CONFIRM, createButton(Material.LIME_CONCRETE, "dialog.item_select_confirm"));
        inventory.setItem(SLOT_CANCEL, createButton(Material.RED_CONCRETE, "dialog.item_select_cancel"));
        inventory.setItem(SLOT_ITEM, selectedItem != null ? selectedItem : createPlaceholder());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0) {
            return;
        }

        if (rawSlot < SIZE) {
            event.setCancelled(true);
            if (rawSlot == SLOT_CONFIRM) {
                if (selectedItem == null) {
                    viewer.sendMessage(messages.get("error.invalid_material"));
                    return;
                }
                uiManager.openListingCreateDialog(viewer, shop, selectedItem.clone());
                return;
            }
            if (rawSlot == SLOT_CANCEL) {
                uiManager.openShopInventory(viewer, shop);
                return;
            }
            if (rawSlot == SLOT_ITEM) {
                ItemStack cursor = event.getCursor();
                if (isValidItem(cursor)) {
                    selectedItem = cloneSingle(cursor);
                    render();
                } else if (cursor == null || cursor.getType().isAir()) {
                    selectedItem = null;
                    render();
                }
            }
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (isValidItem(clicked)) {
            selectedItem = cloneSingle(clicked);
            render();
        }
    }

    private ItemStack createPlaceholder() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("dialog.item_select_slot_name"));
        meta.lore(List.of(messages.get("dialog.item_select_slot_hint")));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createButton(Material material, String key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get(key));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isValidItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }

    private static ItemStack cloneSingle(ItemStack item) {
        ItemStack cloned = item.clone();
        cloned.setAmount(1);
        return cloned;
    }
}

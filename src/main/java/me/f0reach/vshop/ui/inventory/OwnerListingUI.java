package me.f0reach.vshop.ui.inventory;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import me.f0reach.vshop.ui.inventory.base.PaginatedInventoryUI;
import me.f0reach.vshop.ui.inventory.item.InventoryItemBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public final class OwnerListingUI extends PaginatedInventoryUI {
    private static final int ADD_BUTTON_SLOT = 44;

    private final Shop shop;
    private final UIManager uiManager;

    public OwnerListingUI(Player viewer, Shop shop, MessageManager messages, UIManager uiManager) {
        super(viewer, messages.get("shop.inventory_title", "shop_id", String.valueOf(shop.shopId())), messages);
        this.shop = shop;
        this.uiManager = uiManager;
    }

    @Override
    protected List<Listing> getListings() {
        try {
            return uiManager.getShopService().getListingRepo().findByShopId(shop.shopId());
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to load listings", e);
            return Collections.emptyList();
        }
    }

    @Override
    protected void renderContentSlots(List<Listing> pageListings, int offset) {
        for (int i = 0; i < pageListings.size(); i++) {
            inventory.setItem(i, InventoryItemBuilder.buildListingItem(pageListings.get(i), messages, true));
        }
        // Add button in last content slot
        inventory.setItem(ADD_BUTTON_SLOT, createAddButton());
    }

    @Override
    protected void handleContentClick(InventoryClickEvent event, int slot, Listing listing) {
        if (slot == ADD_BUTTON_SLOT) {
            uiManager.openItemSelectUI(viewer, shop);
            return;
        }
        uiManager.openListingEditDialog(viewer, shop, listing);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        // Handle add button even if no listing at that slot
        if (slot == ADD_BUTTON_SLOT) {
            uiManager.openItemSelectUI(viewer, shop);
            return;
        }
        super.handleClick(event);
    }

    private ItemStack createAddButton() {
        ItemStack item = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.get("shop.owner_add_button"));
        item.setItemMeta(meta);
        return item;
    }

    public Shop getShop() { return shop; }
}

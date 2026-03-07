package me.f0reach.vshop.ui.inventory;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.ui.UIManager;
import me.f0reach.vshop.ui.inventory.base.PaginatedInventoryUI;
import me.f0reach.vshop.ui.inventory.item.InventoryItemBuilder;
import me.f0reach.vshop.ui.inventory.item.NavigationItems;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class OwnerListingUI extends PaginatedInventoryUI {
    private final Shop shop;
    private final UIManager uiManager;
    private static final int SLOT_STORAGE = 50;

    public OwnerListingUI(Player viewer, Shop shop, MessageManager messages, UIManager uiManager) {
        super(viewer, messages.get("shop.inventory_title",
                Placeholder.unparsed("shop_id", String.valueOf(shop.shopId()))), messages);
        this.shop = shop;
        this.uiManager = uiManager;
    }

    @Override
    protected List<Listing> getListings() {
        try {
            return uiManager.getShopService().getListingsForDisplay(shop, true);
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to load listings", e);
            return Collections.emptyList();
        }
    }

    @Override
    protected void renderContentSlots(Map<Integer, Listing> pageListings) {
        for (Map.Entry<Integer, Listing> entry : pageListings.entrySet()) {
            inventory.setItem(entry.getKey(), InventoryItemBuilder.buildListingItem(entry.getValue(), messages, true));
        }
    }

    @Override
    protected void handleContentClick(InventoryClickEvent event, int slot, Listing listing) {
        uiManager.openListingEditDialog(viewer, shop, listing);
    }

    @Override
    protected void handleEmptyContentClick(InventoryClickEvent event, int slot) {
        if (!event.getView().getTopInventory().equals(event.getClickedInventory())) {
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) {
            return;
        }
        uiManager.openListingCreateDialog(viewer, shop, cursor.clone(), toAbsoluteSlot(slot));
    }

    @Override
    protected boolean hasTrailingCreatePage() { return true; }

    @Override
    protected void renderNavExtras() {
        if (shop.type() == ShopType.PLAYER) {
            inventory.setItem(SLOT_STORAGE, NavigationItems.openStorage(messages));
        }
    }

    @Override
    protected boolean handleCustomNavClick(InventoryClickEvent event, int slot) {
        if (slot == SLOT_STORAGE && shop.type() == ShopType.PLAYER) {
            uiManager.openShopStorageInventory(viewer, shop);
            return true;
        }
        return false;
    }

    public Shop getShop() { return shop; }
}

package me.f0reach.vshop.ui.inventory;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.ui.UIManager;
import me.f0reach.vshop.ui.inventory.base.PaginatedInventoryUI;
import me.f0reach.vshop.ui.inventory.item.InventoryItemBuilder;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ShopListingUI extends PaginatedInventoryUI {
    private final Shop shop;
    private final UIManager uiManager;

    public ShopListingUI(Player viewer, Shop shop, MessageManager messages, UIManager uiManager) {
        super(viewer, messages.get("shop.inventory_title",
                Placeholder.unparsed("shop_id", String.valueOf(shop.shopId()))), messages);
        this.shop = shop;
        this.uiManager = uiManager;
    }

    @Override
    protected List<Listing> getListings() {
        try {
            return uiManager.getShopService().getListingsForDisplay(shop, false);
        } catch (SQLException e) {
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to load listings", e);
            return Collections.emptyList();
        }
    }

    @Override
    protected void renderContentSlots(Map<Integer, Listing> pageListings) {
        for (Map.Entry<Integer, Listing> entry : pageListings.entrySet()) {
            ShopService.TradeAccess tradeAccess = null;
            try {
                tradeAccess = uiManager.getShopService().getTradeAccess(viewer, entry.getValue());
            } catch (SQLException e) {
                viewer.getServer().getLogger().log(Level.WARNING, "Failed to load trade access state", e);
            }
            inventory.setItem(entry.getKey(), InventoryItemBuilder.buildListingItem(
                    entry.getValue(),
                    messages,
                    uiManager.getShopService().getEconomy(),
                    false,
                    tradeAccess
            ));
        }
    }

    @Override
    protected void handleContentClick(InventoryClickEvent event, int slot, Listing listing) {
        uiManager.openTradeConfirmDialog(viewer, shop, listing);
    }

    public Shop getShop() { return shop; }
}

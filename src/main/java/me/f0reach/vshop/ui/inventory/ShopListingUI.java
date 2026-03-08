package me.f0reach.vshop.ui.inventory;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingWithAccess;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ShopListingUI extends PaginatedInventoryUI {
    private final Shop shop;
    private final UIManager uiManager;
    private final Map<Integer, ListingWithAccess> accessByListingId = new HashMap<>();

    public ShopListingUI(Player viewer, Shop shop, MessageManager messages, UIManager uiManager) {
        super(viewer, messages.get("shop.inventory_title",
                Placeholder.unparsed("shop_id", String.valueOf(shop.shopId()))), messages);
        this.shop = shop;
        this.uiManager = uiManager;
    }

    @Override
    protected List<Listing> getListings() {
        try {
            List<ListingWithAccess> entries = uiManager.getShopService()
                    .getListingsForDisplayWithAccess(shop, false, viewer.getUniqueId());
            accessByListingId.clear();
            for (ListingWithAccess entry : entries) {
                accessByListingId.put(entry.listing().listingId(), entry);
            }
            return entries.stream().map(ListingWithAccess::listing).toList();
        } catch (SQLException e) {
            accessByListingId.clear();
            viewer.getServer().getLogger().log(Level.SEVERE, "Failed to load listings", e);
            return Collections.emptyList();
        }
    }

    @Override
    protected void renderContentSlots(Map<Integer, Listing> pageListings) {
        for (Map.Entry<Integer, Listing> entry : pageListings.entrySet()) {
            ListingWithAccess displayEntry = accessByListingId.get(entry.getValue().listingId());
            inventory.setItem(entry.getKey(), InventoryItemBuilder.buildListingItem(
                    entry.getValue(),
                    messages,
                    uiManager.getShopService().getEconomy(),
                    false,
                    displayEntry != null ? displayEntry.access() : null
            ));
        }
    }

    @Override
    protected void handleContentClick(InventoryClickEvent event, int slot, Listing listing) {
        uiManager.openTradeConfirmDialog(viewer, shop, listing);
    }

    public Shop getShop() { return shop; }
}

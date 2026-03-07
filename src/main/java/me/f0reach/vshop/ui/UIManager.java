package me.f0reach.vshop.ui;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.ui.dialog.*;
import me.f0reach.vshop.ui.inventory.ItemSelectUI;
import me.f0reach.vshop.ui.inventory.OwnerListingUI;
import me.f0reach.vshop.ui.inventory.ShopListingUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class UIManager {
    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final ShopService shopService;
    private final DialogFactory dialogFactory;

    public UIManager(JavaPlugin plugin, MessageManager messages, ShopService shopService) {
        this.plugin = plugin;
        this.messages = messages;
        this.shopService = shopService;
        this.dialogFactory = new DialogFactory(messages);
    }

    public void openShopInventory(Player viewer, Shop shop) {
        boolean isOwner = shop.type() == ShopType.PLAYER
                && shop.ownerUuid() != null
                && shop.ownerUuid().equals(viewer.getUniqueId());
        boolean isAdmin = viewer.hasPermission("modernvillagershop.admin");

        if (isOwner || (isAdmin && shop.type() == ShopType.ADMIN)) {
            new OwnerListingUI(viewer, shop, messages, this).open();
        } else {
            new ShopListingUI(viewer, shop, messages, this).open();
        }
    }

    public void openShopInitDialog(Player viewer, int shopId) {
        try {
            viewer.showDialog(ShopInitDialog.create(dialogFactory, shopId));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show init dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    public void openItemSelectUI(Player viewer, Shop shop) {
        new ItemSelectUI(viewer, shop, messages, this).open();
    }

    public void openListingCreateDialog(Player viewer, Shop shop, ItemStack selectedItem) {
        try {
            viewer.showDialog(ListingCreateDialog.create(dialogFactory, shop, selectedItem, this));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show create dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    public void openPriceQuantityDialog(Player viewer, Shop shop, ListingMode mode, Listing existingListing, ItemStack selectedItem) {
        try {
            viewer.showDialog(PriceQuantityDialog.create(dialogFactory, shop, mode, existingListing, selectedItem, this));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show price dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    public void openListingEditDialog(Player viewer, Shop shop, Listing listing) {
        try {
            viewer.showDialog(ListingEditDialog.create(dialogFactory, shop, listing, this));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show edit dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    public void openTradeConfirmDialog(Player viewer, Shop shop, Listing listing) {
        try {
            viewer.showDialog(TradeConfirmDialog.create(dialogFactory, shop, listing, this));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show trade dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    public void openDeleteConfirmDialog(Player viewer, Shop shop, Listing listing) {
        try {
            viewer.showDialog(DeleteConfirmDialog.create(dialogFactory, shop, listing, this));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show delete dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    // --- Handlers called by dialog callbacks ---

    public void handleListingCreate(Player player, Shop shop, ListingMode mode, ItemStack selectedItem, float price, int stock) {
        if (selectedItem == null || selectedItem.getType().isAir()) {
            player.sendMessage(messages.get("error.invalid_material"));
            return;
        }

        ItemStack template = selectedItem.clone();
        template.setAmount(1);
        byte[] serialized = template.serializeAsBytes();

        try {
            int targetStock = mode == ListingMode.BUY ? stock : 0;
            int actualStock = mode == ListingMode.SELL ? stock : 0;
            int result = shopService.addListing(shop.shopId(), mode, serialized, price, actualStock, targetStock);
            if (result == -1) {
                player.sendMessage(messages.get("error.type_limit_exceeded"));
            } else {
                player.sendMessage(messages.get("shop.listing_added"));
                // Reopen owner UI
                shopService.getShopById(shop.shopId()).ifPresent(s -> openShopInventory(player, s));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create listing", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleListingPriceUpdate(Player player, Listing listing, float price, int stock, ListingMode mode) {
        try {
            int targetStock = mode == ListingMode.BUY ? stock : listing.targetStock();
            int actualStock = mode == ListingMode.SELL ? stock : listing.stock();
            shopService.getListingRepo().updatePriceAndStock(listing.listingId(), price, actualStock, targetStock);
            player.sendMessage(messages.get("shop.offer_updated",
                    "shop_id", String.valueOf(listing.shopId()),
                    "mode", listing.mode().name(),
                    "item", getItemName(listing),
                    "price", String.format("%.2f", (double) price)));
            shopService.getShopById(listing.shopId()).ifPresent(s -> openShopInventory(player, s));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update listing", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleListingToggle(Player player, Listing listing) {
        try {
            boolean newState = !listing.enabled();
            shopService.getListingRepo().setEnabled(listing.listingId(), newState);
            if (newState) {
                player.sendMessage(messages.get("shop.listing_toggled_on"));
            } else {
                player.sendMessage(messages.get("shop.listing_toggled_off"));
            }
            shopService.getShopById(listing.shopId()).ifPresent(s -> openShopInventory(player, s));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to toggle listing", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleListingDelete(Player player, Shop shop, Listing listing) {
        try {
            shopService.getListingRepo().delete(listing.listingId());
            player.sendMessage(messages.get("shop.listing_deleted"));
            openShopInventory(player, shop);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete listing", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleShopDelete(Player player, Shop shop) {
        try {
            shopService.getShopRepo().delete(shop.shopId());
            player.sendMessage(messages.get("shop.deleted"));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete shop", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleTradeExecution(Player player, Shop shop, Listing listing) {
        // Re-fetch listing to get latest stock
        try {
            var freshListing = shopService.getListingRepo().findById(listing.listingId());
            if (freshListing.isEmpty()) {
                player.sendMessage(messages.get("error.listing_not_found"));
                return;
            }
            Listing current = freshListing.get();
            ShopService.TradeResult result;
            if (current.mode() == ListingMode.SELL) {
                result = shopService.executeSellTrade(player, current, shop);
            } else {
                result = shopService.executeBuyTrade(player, current, shop);
            }

            switch (result) {
                case SUCCESS -> {
                    String itemName = getItemName(current);
                    String price = String.format("%.2f", current.unitPrice());
                    if (current.mode() == ListingMode.SELL) {
                        player.sendMessage(messages.get("trade.purchase_success", "item", itemName, "price", price));
                    } else {
                        player.sendMessage(messages.get("trade.procurement_success", "item", itemName, "price", price));
                    }
                }
                case OUT_OF_STOCK -> player.sendMessage(messages.get("trade.out_of_stock"));
                case BALANCE_SHORTAGE -> player.sendMessage(messages.get("trade.balance_shortage"));
                case NO_ITEM -> player.sendMessage(messages.get("trade.no_item_to_sell"));
                case BUY_ORDER_FULL -> player.sendMessage(messages.get("trade.buy_order_full"));
                case OWNER_INSUFFICIENT -> player.sendMessage(messages.get("trade.shop_owner_insufficient"));
                case TRADE_FAILED -> player.sendMessage(messages.get("error.trade_failed"));
                case LISTING_NOT_FOUND -> player.sendMessage(messages.get("error.listing_not_found"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Trade execution failed", e);
            player.sendMessage(messages.get("error.trade_failed"));
        }
    }

    private String getItemName(Listing listing) {
        try {
            ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
            return item.getType().name();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    public ShopService getShopService() { return shopService; }
    public MessageManager getMessages() { return messages; }
}

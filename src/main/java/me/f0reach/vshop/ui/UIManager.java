package me.f0reach.vshop.ui;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.ui.dialog.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import me.f0reach.vshop.ui.inventory.ItemSelectUI;
import me.f0reach.vshop.ui.inventory.OwnerListingUI;
import me.f0reach.vshop.ui.inventory.ShopStorageUI;
import me.f0reach.vshop.ui.inventory.ShopListingUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
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
        if (canOpenManagementInventory(viewer, shop)) {
            openShopManagementInventory(viewer, shop);
            return;
        }
        openShopTradingInventory(viewer, shop);
    }

    public void openShopManagementInventory(Player viewer, Shop shop) {
        new OwnerListingUI(viewer, shop, messages, this).open();
    }

    public void openShopTradingInventory(Player viewer, Shop shop) {
        new ShopListingUI(viewer, shop, messages, this).open();
    }

    private boolean canOpenManagementInventory(Player viewer, Shop shop) {
        if (shop.type() == ShopType.PLAYER) {
            return shop.ownerUuid() != null && shop.ownerUuid().equals(viewer.getUniqueId());
        }
        return shop.type() == ShopType.ADMIN
                && viewer.hasPermission("modernvillagershop.admin")
                && viewer.isSneaking();
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

    public void openShopStorageInventory(Player viewer, Shop shop) {
        new ShopStorageUI(viewer, shop, messages, this).open();
    }

    public void openListingCreateDialog(Player viewer, Shop shop, ItemStack selectedItem) {
        int emptySlot = findFirstEmptySlot(shop);
        if (emptySlot < 0) {
            viewer.sendMessage(messages.get("error.no_empty_slot"));
            return;
        }
        openListingCreateDialog(viewer, shop, selectedItem, emptySlot);
    }

    public void openListingCreateDialog(Player viewer, Shop shop, ItemStack selectedItem, int uiSlot) {
        try {
            viewer.showDialog(ListingCreateDialog.create(dialogFactory, shop, selectedItem, uiSlot, this));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to show create dialog", e);
            viewer.sendMessage(messages.get("dialog.fallback_notice"));
        }
    }

    public void openPriceQuantityDialog(Player viewer, Shop shop, ListingMode mode, Listing existingListing,
            ItemStack selectedItem) {
        openPriceQuantityDialog(viewer, shop, mode, existingListing, selectedItem, -1);
    }

    public void openPriceQuantityDialog(Player viewer, Shop shop, ListingMode mode, Listing existingListing,
            ItemStack selectedItem, int uiSlot) {
        try {
            viewer.showDialog(
                    PriceQuantityDialog.create(dialogFactory, shop, mode, existingListing, selectedItem, uiSlot, this));
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

    public void handleListingCreate(Player player, Shop shop, ListingMode mode, ItemStack selectedItem,
            double price, int tradeQuantity, int stock, int uiSlot) {
        if (selectedItem == null || selectedItem.getType().isAir()) {
            player.sendMessage(messages.get("error.invalid_material"));
            return;
        }
        if (uiSlot < 0) {
            player.sendMessage(messages.get("error.invalid_slot"));
            return;
        }

        ItemStack template = selectedItem.clone();
        template.setAmount(1);
        byte[] serialized = template.serializeAsBytes();
        double roundedPrice = shopService.getEconomy().roundToFractionalDigits(price);

        try {
            if (shopService.getListingRepo().existsByShopIdAndSlot(shop.shopId(), uiSlot)) {
                player.sendMessage(messages.get("error.slot_occupied"));
                shopService.getShopById(shop.shopId()).ifPresent(s -> openShopManagementInventory(player, s));
                return;
            }
            int targetStock = mode == ListingMode.BUY ? stock : 0;
            int actualStock = 0;
            int result = shopService.addListing(shop.shopId(), uiSlot, mode, serialized, roundedPrice, tradeQuantity,
                    actualStock, targetStock);
            if (result == -1) {
                player.sendMessage(messages.get("error.type_limit_exceeded"));
            } else {
                player.sendMessage(messages.get("shop.listing_added"));
                // Reopen owner UI
                shopService.getShopById(shop.shopId()).ifPresent(s -> openShopManagementInventory(player, s));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create listing", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleListingPriceUpdate(Player player, Listing listing, double price, int tradeQuantity, int stock,
            ListingMode mode) {
        try {
            int targetStock = mode == ListingMode.BUY ? stock : listing.targetStock();
            int actualStock = listing.stock();
            double roundedPrice = getShopService().getEconomy().roundToFractionalDigits(price);
            shopService.getListingRepo().updatePriceStockAndQuantity(listing.listingId(), roundedPrice, tradeQuantity,
                    actualStock, targetStock);
            player.sendMessage(messages.get("shop.offer_updated",
                    Placeholder.unparsed("shop_id", String.valueOf(listing.shopId())),
                    Placeholder.unparsed("mode", listing.mode().name()),
                    Placeholder.component("item", getItemName(listing)),
                    Placeholder.unparsed("price", formatPrice(roundedPrice))));
            shopService.getShopById(listing.shopId()).ifPresent(s -> openShopManagementInventory(player, s));
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
            shopService.getShopById(listing.shopId()).ifPresent(s -> openShopManagementInventory(player, s));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to toggle listing", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    public void handleListingDelete(Player player, Shop shop, Listing listing) {
        try {
            shopService.getListingRepo().delete(listing.listingId());
            player.sendMessage(messages.get("shop.listing_deleted"));
            openShopManagementInventory(player, shop);
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
                    var itemComponent = getItemName(current);
                    String price = formatPrice(current.unitPrice());
                    String qty = String.valueOf(current.tradeQuantity());
                    player.sendMessage(messages.get(
                            current.mode() == ListingMode.SELL ? "trade.purchase_success" : "trade.procurement_success",
                            Placeholder.component("item", itemComponent),
                            Placeholder.unparsed("qty", qty),
                            Placeholder.unparsed("price", price)));

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

    public Component getItemName(Listing listing) {
        try {
            ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
            return Component.translatable(item).hoverEvent(item.asHoverEvent());
        } catch (Exception e) {
            return Component.text("UNKNOWN");
        }
    }

    public String formatPrice(double price) {
        return shopService.getEconomy().format(price);
    }

    public String formatPriceAsNumber(double price) {
        return shopService.getEconomy().formatAsNumber(price);
    }

    public ShopService getShopService() {
        return shopService;
    }

    public MessageManager getMessages() {
        return messages;
    }

    private int findFirstEmptySlot(Shop shop) {
        try {
            Set<Integer> occupied = new HashSet<>();
            for (Listing listing : shopService.getListingRepo().findByShopId(shop.shopId())) {
                if (listing.uiSlot() >= 0) {
                    occupied.add(listing.uiSlot());
                }
            }
            for (int slot = 0;; slot++) {
                if (!occupied.contains(slot)) {
                    return slot;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to find empty slot", e);
        }
        return -1;
    }
}

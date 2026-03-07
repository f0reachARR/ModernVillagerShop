package me.f0reach.vshop.ui.inventory.item;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class InventoryItemBuilder {
    private InventoryItemBuilder() {}

    public static ItemStack buildListingItem(Listing listing, MessageManager messages, boolean showDisabled) {
        ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
        int quantity = listing.tradeQuantity();
        item.setAmount(Math.min(quantity, Math.max(1, item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();

        String itemName = item.getType().name();
        meta.displayName(messages.get("shop.listing_name", "item", itemName));

        List<Component> lore = new ArrayList<>();
        lore.add(messages.get("shop.listing_lore_mode", "mode", listing.mode().name()));
        lore.add(messages.get("shop.listing_lore_price",
                "qty", String.valueOf(quantity),
                "price", String.format("%.2f", listing.unitPrice())));
        lore.add(messages.get("shop.listing_lore_stock",
                "stock", String.valueOf(listing.stock()),
                "max_stock", String.valueOf(listing.targetStock())));
        lore.add(messages.get("shop.listing_lore_click"));

        if (showDisabled && !listing.enabled()) {
            lore.add(messages.get("shop.listing_lore_disabled"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

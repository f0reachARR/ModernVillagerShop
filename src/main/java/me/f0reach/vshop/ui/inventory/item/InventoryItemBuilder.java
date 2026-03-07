package me.f0reach.vshop.ui.inventory.item;

import me.f0reach.vshop.economy.VaultEconomyAdapter;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.ShopService.TradeResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class InventoryItemBuilder {
    private InventoryItemBuilder() {
    }

    public static ItemStack buildListingItem(Listing listing, MessageManager messages,
            VaultEconomyAdapter economy, boolean showDisabled, ShopService.TradeAccess tradeAccess) {
        ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
        int quantity = listing.tradeQuantity();
        item.setAmount(Math.min(quantity, Math.max(1, item.getMaxStackSize())));
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();
        lore.add(messages.get("shop.listing_lore_mode",
                Placeholder.unparsed("mode", listing.mode().name())));
        lore.add(messages.get("shop.listing_lore_price",
                Placeholder.unparsed("qty", String.valueOf(quantity)),
                Placeholder.unparsed("price", economy.format(listing.unitPrice()))));
        lore.add(messages.get("shop.listing_lore_stock",
                Placeholder.unparsed("stock", String.valueOf(listing.stock())),
                Placeholder.unparsed("max_stock", String.valueOf(listing.targetStock()))));
        if (tradeAccess.blockedReason() == TradeResult.COOLDOWN_ACTIVE) {
            lore.add(messages.get("shop.listing_lore_cooldown_remaining",
                    Placeholder.unparsed("seconds", String.valueOf(tradeAccess.remainingCooldownSeconds()))));
        } else if (tradeAccess.blockedReason() == TradeResult.LIFETIME_LIMIT_REACHED) {
            lore.add(messages.get("shop.listing_lore_lifetime_remaining",
                    Placeholder.unparsed("remaining", String.valueOf(tradeAccess.remainingLifetimeTrades())),
                    Placeholder.unparsed("limit", String.valueOf(listing.lifetimeLimitPerPlayer()))));
        }
        lore.add(messages.get("shop.listing_lore_click"));

        if (showDisabled && !listing.enabled()) {
            lore.add(messages.get("shop.listing_lore_disabled"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

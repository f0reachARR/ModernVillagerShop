package me.f0reach.vshop.ui.inventory.item;

import me.f0reach.vshop.economy.VaultEconomyAdapter;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.TradeAccessBlockReason;
import me.f0reach.vshop.model.TradeAccessSnapshot;
import me.f0reach.vshop.shop.ShopService;
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
        return buildListingItem(listing, messages, economy, showDisabled, tradeAccess == null
                ? TradeAccessSnapshot.unrestricted()
                : fromTradeAccess(tradeAccess));
    }

    public static ItemStack buildListingItem(Listing listing, MessageManager messages,
            VaultEconomyAdapter economy, boolean showDisabled, TradeAccessSnapshot tradeAccess) {
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
        if (tradeAccess != null && tradeAccess.blockedReason() == TradeAccessBlockReason.COOLDOWN_ACTIVE) {
            lore.add(messages.get("shop.listing_lore_cooldown_remaining",
                    Placeholder.unparsed("seconds",
                            String.valueOf(tradeAccess.remainingCooldownSeconds()))));
        } else if (tradeAccess != null
                && tradeAccess.blockedReason() == TradeAccessBlockReason.LIFETIME_LIMIT_REACHED) {
            lore.add(messages.get("shop.listing_lore_lifetime_remaining",
                    Placeholder.unparsed("remaining",
                            String.valueOf(tradeAccess.remainingLifetimeTrades())),
                    Placeholder.unparsed("limit",
                            String.valueOf(listing.lifetimeLimitPerPlayer()))));
        } else {
            lore.add(messages.get("shop.listing_lore_click"));
        }

        if (showDisabled && !listing.enabled()) {
            lore.add(messages.get("shop.listing_lore_disabled"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static TradeAccessSnapshot fromTradeAccess(ShopService.TradeAccess tradeAccess) {
        if (tradeAccess.blockedReason() == null) {
            return new TradeAccessSnapshot(
                    TradeAccessBlockReason.NONE,
                    tradeAccess.remainingCooldownSeconds(),
                    tradeAccess.remainingLifetimeTrades());
        }
        return new TradeAccessSnapshot(
                switch (tradeAccess.blockedReason()) {
                    case COOLDOWN_ACTIVE -> TradeAccessBlockReason.COOLDOWN_ACTIVE;
                    case LIFETIME_LIMIT_REACHED -> TradeAccessBlockReason.LIFETIME_LIMIT_REACHED;
                    default -> TradeAccessBlockReason.NONE;
                },
                tradeAccess.remainingCooldownSeconds(),
                tradeAccess.remainingLifetimeTrades());
    }
}

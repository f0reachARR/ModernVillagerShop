package me.f0reach.vshop.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class TradeConfirmDialog {
        private TradeConfirmDialog() {
        }

        public static Dialog create(DialogFactory factory, Shop shop, Listing listing, UIManager uiManager) {
                ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
                int quantity = listing.tradeQuantity();
                item.setAmount(Math.min(quantity, Math.max(1, item.getMaxStackSize())));
                String itemName = item.getType().name();
                String price = uiManager.formatPrice(listing.unitPrice());

                String bodyKey = listing.mode() == ListingMode.SELL
                                ? "dialog.trade_confirm_buy_body"
                                : "dialog.trade_confirm_sell_body";

                return Dialog.create(builder -> builder.empty()
                                .base(DialogBase.builder(factory.text("dialog.trade_confirm_title"))
                                                .body(List.of(
                                                                DialogBody.plainMessage(factory.text(bodyKey,
                                                                                Placeholder.unparsed("item", itemName),
                                                                                Placeholder.unparsed("qty", String
                                                                                                .valueOf(quantity)),
                                                                                Placeholder.unparsed("price", price))),
                                                                DialogBody.item(item).build()))
                                                .build())
                                .type(DialogType.confirmation(
                                                ActionButton.builder(factory.text("dialog.trade_confirm_yes"))
                                                                .action(DialogAction.customClick(
                                                                                (view, audience) -> {
                                                                                        if (audience instanceof Player player) {
                                                                                                uiManager.handleTradeExecution(
                                                                                                                player,
                                                                                                                shop,
                                                                                                                listing);
                                                                                        }
                                                                                },
                                                                                factory.singleUseOptions()))
                                                                .build(),
                                                ActionButton.builder(factory.text("dialog.trade_confirm_no"))
                                                                .action(null)
                                                                .build())));
        }
}

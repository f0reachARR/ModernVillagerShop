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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class TradeConfirmDialog {
    private TradeConfirmDialog() {}

    public static Dialog create(DialogFactory factory, Shop shop, Listing listing, UIManager uiManager) {
        ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
        String itemName = item.getType().name();
        String price = String.format("%.2f", listing.unitPrice());

        String bodyKey = listing.mode() == ListingMode.SELL
                ? "dialog.trade_confirm_buy_body"
                : "dialog.trade_confirm_sell_body";

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(factory.text("dialog.trade_confirm_title"))
                        .body(List.of(
                                DialogBody.plainMessage(factory.text(bodyKey,
                                        "item", itemName, "qty", "1", "price", price)),
                                DialogBody.item(item).build()
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(factory.text("dialog.trade_confirm_yes"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                uiManager.handleTradeExecution(player, shop, listing);
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.trade_confirm_no"))
                                .action(null)
                                .build()
                ))
        );
    }
}

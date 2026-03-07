package me.f0reach.vshop.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class ListingCreateDialog {
    private ListingCreateDialog() {}

    public static Dialog create(DialogFactory factory, Shop shop, ItemStack selectedItem, int uiSlot, UIManager uiManager) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(factory.text("dialog.listing_create_title"))
                        .body(List.of(
                                DialogBody.plainMessage(factory.text("dialog.listing_create_body"))
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(factory.text("dialog.listing_create_sell"))
                                .tooltip(factory.text("dialog.listing_create_sell_tooltip"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                uiManager.openPriceQuantityDialog(player, shop, ListingMode.SELL, null, selectedItem, uiSlot);
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.listing_create_buy"))
                                .tooltip(factory.text("dialog.listing_create_buy_tooltip"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                uiManager.openPriceQuantityDialog(player, shop, ListingMode.BUY, null, selectedItem, uiSlot);
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        factory.cancelButton()
                )).build())
        );
    }
}

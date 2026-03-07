package me.f0reach.vshop.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import org.bukkit.entity.Player;

import java.util.List;

public final class ListingEditDialog {
    private ListingEditDialog() {}

    public static Dialog create(DialogFactory factory, Shop shop, Listing listing, UIManager uiManager) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(factory.text("dialog.edit_title"))
                        .body(List.of(
                                DialogBody.plainMessage(factory.text("dialog.edit_body"))
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(factory.text("dialog.edit_price"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                uiManager.openPriceQuantityDialog(player, shop, listing.mode(), listing, null);
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.edit_toggle"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                uiManager.handleListingToggle(player, listing);
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.edit_delete"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                uiManager.openDeleteConfirmDialog(player, shop, listing);
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.edit_cancel"))
                                .action(null)
                                .build()
                )).build())
        );
    }
}

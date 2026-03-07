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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DeleteConfirmDialog {
    private DeleteConfirmDialog() {}

    /**
     * Create delete confirmation dialog.
     * If listing is null, this is a shop delete confirmation.
     * If listing is non-null, this is a listing delete confirmation.
     */
    public static Dialog create(DialogFactory factory, Shop shop, @Nullable Listing listing, UIManager uiManager) {
        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(factory.text("dialog.delete_title"))
                        .canCloseWithEscape(false)
                        .body(List.of(
                                DialogBody.plainMessage(factory.text("dialog.delete_body"))
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(factory.text("dialog.delete_yes"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                if (listing != null) {
                                                    uiManager.handleListingDelete(player, shop, listing);
                                                } else {
                                                    uiManager.handleShopDelete(player, shop);
                                                }
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.delete_no"))
                                .action(null)
                                .build()
                ))
        );
    }
}

package me.f0reach.vshop.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.UIManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PriceQuantityDialog {
    private PriceQuantityDialog() {}

    /**
     * Create dialog for new listing or editing existing listing.
     * @param existingListing null for new listing, non-null for editing
     */
    public static Dialog create(DialogFactory factory, Shop shop, ListingMode mode,
                                @Nullable Listing existingListing, @Nullable ItemStack selectedItem, int uiSlot, UIManager uiManager) {
        float initialPrice = existingListing != null ? (float) existingListing.unitPrice() : 1.0f;
        float initialStock = existingListing != null ? existingListing.stock() : 1f;
        float initialTarget = existingListing != null ? existingListing.targetStock() : 64f;

        List<DialogInput> inputs;
        if (mode == ListingMode.SELL) {
            inputs = List.of(
                    DialogInput.numberRange("price", factory.text("dialog.price_label"), 0.01f, 1000000f)
                            .step(0.01f).initial(initialPrice).width(300).build(),
                    DialogInput.numberRange("stock", factory.text("dialog.stock_label"), 0f, 100000f)
                            .step(1f).initial(initialStock).width(300).build()
            );
        } else {
            inputs = List.of(
                    DialogInput.numberRange("price", factory.text("dialog.price_label"), 0.01f, 1000000f)
                            .step(0.01f).initial(initialPrice).width(300).build(),
                    DialogInput.numberRange("stock", factory.text("dialog.stock_label"), 0f, 100000f)
                            .step(1f).initial(initialTarget).width(300).build()
            );
        }

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(factory.text("dialog.price_title"))
                        .body(List.of(
                                DialogBody.plainMessage(factory.text("dialog.price_body"))
                        ))
                        .inputs(inputs)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(factory.text("dialog.price_confirm"))
                                .action(DialogAction.customClick(
                                        (view, audience) -> {
                                            if (audience instanceof Player player) {
                                                float price = view.getFloat("price");
                                                int stock = view.getFloat("stock").intValue();
                                                if (existingListing != null) {
                                                    uiManager.handleListingPriceUpdate(player, existingListing, price, stock, mode);
                                                } else {
                                                    uiManager.handleListingCreate(player, shop, mode, selectedItem, price, stock, uiSlot);
                                                }
                                            }
                                        },
                                        factory.singleUseOptions()
                                ))
                                .build(),
                        ActionButton.builder(factory.text("dialog.price_cancel"))
                                .action(null)
                                .build()
                ))
        );
    }
}

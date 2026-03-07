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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class PriceQuantityDialog {
    private static final float MIN_PRICE = 0.01f;
    private static final float MAX_PRICE = 1_000_000f;
    private static final int MIN_TRADE_QUANTITY = 1;
    private static final int MIN_STOCK = 0;
    private static final int MAX_STOCK = 100_000;

    private PriceQuantityDialog() {}

    /**
     * Create dialog for new listing or editing existing listing.
     * @param existingListing null for new listing, non-null for editing
     */
    public static Dialog create(DialogFactory factory, Shop shop, ListingMode mode,
                                @Nullable Listing existingListing, @Nullable ItemStack selectedItem, int uiSlot, UIManager uiManager) {
        float initialPrice = existingListing != null ? (float) existingListing.unitPrice() : 1.0f;
        int initialQuantity = existingListing != null ? existingListing.tradeQuantity() : 1;
        int initialTarget = existingListing != null ? existingListing.targetStock() : 64;
        ItemStack templateItem = resolveTemplateItem(existingListing, selectedItem);
        int maxTradeQuantity = Math.max(MIN_TRADE_QUANTITY, templateItem.getMaxStackSize());

        List<DialogInput> inputs;
        if (mode == ListingMode.SELL) {
            inputs = List.of(
                    DialogInput.text("price", factory.text("dialog.price_label"))
                            .initial(String.format("%.2f", (double) initialPrice))
                            .maxLength(16)
                            .width(300)
                            .build(),
                    DialogInput.text("quantity", factory.text("dialog.quantity_label"))
                            .initial(String.valueOf(initialQuantity))
                            .maxLength(4)
                            .width(300)
                            .build()
            );
        } else {
            inputs = List.of(
                    DialogInput.text("price", factory.text("dialog.price_label"))
                            .initial(String.format("%.2f", (double) initialPrice))
                            .maxLength(16)
                            .width(300)
                            .build(),
                    DialogInput.text("quantity", factory.text("dialog.quantity_label"))
                            .initial(String.valueOf(initialQuantity))
                            .maxLength(4)
                            .width(300)
                            .build(),
                    DialogInput.text("stock", factory.text("dialog.stock_label"))
                            .initial(String.valueOf(initialTarget))
                            .maxLength(8)
                            .width(300)
                            .build()
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
                                                Float price = parsePrice(view.getText("price"));
                                                if (price == null) {
                                                    player.sendMessage(factory.text("error.invalid_price_input"));
                                                    return;
                                                }

                                                Integer tradeQuantity = parseTradeQuantity(view.getText("quantity"), maxTradeQuantity);
                                                if (tradeQuantity == null) {
                                                    player.sendMessage(factory.text("error.invalid_quantity_input",
                                                            "max", String.valueOf(maxTradeQuantity)));
                                                    return;
                                                }

                                                int stock = 0;
                                                if (mode == ListingMode.BUY) {
                                                    Integer parsedStock = parseStock(view.getText("stock"));
                                                    if (parsedStock == null) {
                                                        player.sendMessage(factory.text("error.invalid_stock_input"));
                                                        return;
                                                    }
                                                    stock = parsedStock;
                                                }

                                                if (existingListing != null) {
                                                    uiManager.handleListingPriceUpdate(player, existingListing, price, tradeQuantity, stock, mode);
                                                } else {
                                                    uiManager.handleListingCreate(player, shop, mode, selectedItem, price, tradeQuantity, stock, uiSlot);
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

    private static @Nullable Float parsePrice(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            float value = Float.parseFloat(text.trim());
            if (value < MIN_PRICE || value > MAX_PRICE) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ItemStack resolveTemplateItem(@Nullable Listing existingListing, @Nullable ItemStack selectedItem) {
        if (existingListing != null) {
            try {
                ItemStack item = ItemStack.deserializeBytes(existingListing.itemSerialized());
                if (item != null && !item.getType().isAir()) {
                    return item;
                }
            } catch (Exception ignored) {
                // Fallback below.
            }
        }
        if (selectedItem != null && !selectedItem.getType().isAir()) {
            return selectedItem;
        }
        return new ItemStack(Material.STONE);
    }

    private static @Nullable Integer parseTradeQuantity(@Nullable String text, int maxTradeQuantity) {
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            if (value < MIN_TRADE_QUANTITY || value > maxTradeQuantity) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static @Nullable Integer parseStock(@Nullable String text) {
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            if (value < MIN_STOCK || value > MAX_STOCK) {
                return null;
            }
            return value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

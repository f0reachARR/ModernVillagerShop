package me.f0reach.vshop.ui.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import java.util.List;

public final class ShopInitDialog {
    private ShopInitDialog() {}

    public static Dialog create(DialogFactory factory, int shopId) {
        return Dialog.create(builder -> builder.empty()
                .base(factory.baseBuilder("dialog.init_title")
                        .body(List.of(
                                factory.textBody("dialog.init_body")
                        ))
                        .build())
                .type(DialogType.notice(
                        ActionButton.builder(factory.text("dialog.init_ok"))
                                .action(null)
                                .build()
                ))
        );
    }
}

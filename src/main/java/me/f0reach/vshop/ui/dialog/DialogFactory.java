package me.f0reach.vshop.ui.dialog;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import me.f0reach.vshop.locale.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;

import java.util.List;

public final class DialogFactory {
    private final MessageManager messages;

    public DialogFactory(MessageManager messages) {
        this.messages = messages;
    }

    public Component text(String key, String... placeholders) {
        return messages.get(key, placeholders);
    }

    public String raw(String key) {
        return messages.getRaw(key);
    }

    public ActionButton button(String labelKey, DialogAction action) {
        return ActionButton.builder(messages.get(labelKey))
                .action(action)
                .build();
    }

    public ActionButton button(String labelKey, String tooltipKey, DialogAction action) {
        return ActionButton.builder(messages.get(labelKey))
                .tooltip(messages.get(tooltipKey))
                .action(action)
                .build();
    }

    public ActionButton cancelButton() {
        return ActionButton.builder(messages.get("dialog.listing_create_cancel"))
                .action(null)
                .build();
    }

    public DialogBase.Builder baseBuilder(String titleKey) {
        return DialogBase.builder(messages.get(titleKey));
    }

    public DialogBody textBody(String key, String... placeholders) {
        return DialogBody.plainMessage(messages.get(key, placeholders));
    }

    public NumberRangeDialogInput.Builder priceInput(float min, float max) {
        return DialogInput.numberRange("price", messages.get("dialog.price_label"), min, max)
                .step(0.01f)
                .initial(1.0f);
    }

    public NumberRangeDialogInput.Builder stockInput(float min, float max) {
        return DialogInput.numberRange("stock", messages.get("dialog.stock_label"), min, max)
                .step(1f)
                .initial(1f);
    }

    public ClickCallback.Options singleUseOptions() {
        return ClickCallback.Options.builder().uses(1).build();
    }

    public MessageManager getMessages() {
        return messages;
    }
}

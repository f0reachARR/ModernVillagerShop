package me.f0reach.vshop.ui.dialog;

import me.f0reach.bedrockdialog.BedrockDialog;
import me.f0reach.bedrockdialog.dialog.ConfirmDialog;
import me.f0reach.bedrockdialog.dialog.InputDialog;
import me.f0reach.bedrockdialog.dialog.MultiButtonDialog;
import me.f0reach.bedrockdialog.dialog.NoticeDialog;
import me.f0reach.bedrockdialog.input.BooleanInput;
import me.f0reach.bedrockdialog.input.DropdownInput;
import me.f0reach.bedrockdialog.input.TextInput;
import me.f0reach.bedrockdialog.response.InputResponse;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Thin wrapper around the BedrockDialog API. Bedrock-side callbacks can fire
 * off the main thread; this wrapper hops to the main thread before invoking
 * caller-supplied logic so Bukkit API calls inside callers are always safe.
 * {@link #confirmOnce} additionally de-duplicates clicks while a confirm is
 * still pending (matches the spec's "連打抑止" requirement for payments).
 */
public final class DialogService {

    private final Plugin plugin;
    private final Set<UUID> openConfirms = new HashSet<>();

    public DialogService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void notice(Player player, Component title, Component body, Component dismissLabel) {
        notice(player, title, body, dismissLabel, null);
    }

    public void notice(Player player, Component title, Component body, Component dismissLabel,
                       Runnable onDismiss) {
        var builder = NoticeDialog.builder()
                .title(title)
                .body(body)
                .dismissLabel(dismissLabel == null ? Component.text("OK") : dismissLabel);
        if (onDismiss != null) {
            builder.onDismiss(p -> runMain(onDismiss));
        }
        BedrockDialog.get().show(player, builder.build());
    }

    public void confirm(Player player, Component title, Component body,
                        Component yesLabel, Component noLabel,
                        Runnable onYes, Runnable onNo) {
        confirm(player, title, body, yesLabel, noLabel, onYes, onNo, null);
    }

    // onClose fires on Bedrock Esc/close and on Java InputDialog cancel;
    // MultiButton/Confirm on Java clients have no server-visible Esc callback.
    public void confirm(Player player, Component title, Component body,
                        Component yesLabel, Component noLabel,
                        Runnable onYes, Runnable onNo, Runnable onClose) {
        var builder = ConfirmDialog.builder()
                .title(title)
                .body(body)
                .yesLabel(yesLabel == null ? Component.text("Yes") : yesLabel)
                .noLabel(noLabel == null ? Component.text("No") : noLabel)
                .onYes(p -> runMain(onYes))
                .onNo(p -> runMain(onNo));
        if (onClose != null) {
            builder.onClose(p -> runMain(onClose));
        }
        BedrockDialog.get().show(player, builder.build());
    }

    public void confirmOnce(Player player, Component title, Component body,
                            Component yesLabel, Component noLabel,
                            Runnable onYes, Runnable onNo) {
        confirmOnce(player, title, body, yesLabel, noLabel, onYes, onNo, null);
    }

    public void confirmOnce(Player player, Component title, Component body,
                            Component yesLabel, Component noLabel,
                            Runnable onYes, Runnable onNo, Runnable onClose) {
        UUID id = player.getUniqueId();
        if (!openConfirms.add(id)) {
            return;
        }
        confirm(player, title, body, yesLabel, noLabel,
                () -> { openConfirms.remove(id); if (onYes != null) onYes.run(); },
                () -> { openConfirms.remove(id); if (onNo != null) onNo.run(); },
                () -> { openConfirms.remove(id); if (onClose != null) onClose.run(); });
    }

    public void multiButton(Player player, Component title, Component body, List<ButtonSpec> buttons) {
        multiButton(player, title, body, buttons, null);
    }

    public void multiButton(Player player, Component title, Component body, List<ButtonSpec> buttons,
                            Runnable onClose) {
        MultiButtonDialog.Builder builder = MultiButtonDialog.builder()
                .title(title)
                .body(body);
        for (ButtonSpec b : buttons) {
            Runnable action = b.action();
            builder.button(b.label(), p -> runMain(action));
        }
        if (onClose != null) {
            builder.onClose(p -> runMain(onClose));
        }
        BedrockDialog.get().show(player, builder.build());
    }

    public InputBuilder input(Player player, Component title, Component body, Component submitLabel) {
        return new InputBuilder(player, title, body, submitLabel);
    }

    public void close(Player player) {
        BedrockDialog.get().closeDialog(player);
    }

    private void runMain(Runnable r) {
        if (r == null) return;
        Bukkit.getScheduler().runTask(plugin, r);
    }

    public record ButtonSpec(Component label, Runnable action) {}

    public final class InputBuilder {
        private final Player player;
        private final InputDialog.Builder backing;

        InputBuilder(Player player, Component title, Component body, Component submitLabel) {
            this.player = player;
            this.backing = InputDialog.builder()
                    .title(title)
                    .body(body)
                    .submitLabel(submitLabel == null ? Component.text("Submit") : submitLabel);
        }

        public InputBuilder text(String key, Component label, String defaultValue) {
            backing.addInput(TextInput.builder(key)
                    .label(label)
                    .defaultValue(defaultValue == null ? "" : defaultValue)
                    .build());
            return this;
        }

        public InputBuilder bool(String key, Component label, boolean defaultValue) {
            backing.addInput(BooleanInput.builder(key)
                    .label(label)
                    .defaultValue(defaultValue)
                    .build());
            return this;
        }

        public InputBuilder dropdown(String key, Component label, List<Option> options, int defaultIndex) {
            var b = DropdownInput.builder(key).label(label).defaultIndex(defaultIndex);
            for (Option o : options) b.addOption(o.id(), o.label());
            backing.addInput(b.build());
            return this;
        }

        // Cancel button on Java + Esc/close on Bedrock. Java Esc alone
        // is not deliverable via Paper Dialog API — the cancel button is
        // the only Java trigger.
        public InputBuilder onCancel(Runnable handler) {
            backing.onClose(p -> runMain(handler));
            return this;
        }

        public void onSubmit(Consumer<InputResponse> handler) {
            backing.onSubmit((p, response) -> runMain(() -> handler.accept(response)));
            BedrockDialog.get().show(player, backing.build());
        }

        public record Option(String id, Component label) {}
    }
}

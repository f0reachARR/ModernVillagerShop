package me.f0reach.vshop.ui.text;

import me.f0reach.vshop.locale.EnumLabels;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.Map;

/**
 * Chat rendering for {@code /vshop appearance <id> show}.
 *
 * <p>Unset values print the {@code none} placeholder rather than being skipped,
 * so the output doubles as a list of the knobs that exist.
 */
public final class AppearanceView {

    private final MessageManager messages;
    private final EnumLabels enumLabels;

    public AppearanceView(MessageManager messages, EnumLabels enumLabels) {
        this.messages = messages;
        this.enumLabels = enumLabels;
    }

    public void send(Audience to, Shop shop, ShopAppearance appearance) {
        to.sendMessage(messages.get("command.appearance.header",
                Placeholder.component("shop_name",
                        Displays.nameWithHover(Displays.truncate(shop.name(), 32), shop.name()))));

        to.sendMessage(messages.get("command.appearance.line-backend",
                Placeholder.component("backend", enumLabels.label(appearance.backend()))));
        to.sendMessage(messages.get("command.appearance.line-type",
                Placeholder.component("type", text(appearance.entityType() == null
                        ? null : appearance.entityType().name()))));
        to.sendMessage(messages.get("command.appearance.line-skin",
                Placeholder.component("skin", text(appearance.skin())),
                Placeholder.component("variant", appearance.skinVariant() == null
                        ? none() : enumLabels.label(appearance.skinVariant()))));
        to.sendMessage(messages.get("command.appearance.line-glow",
                Placeholder.component("state", state(appearance.glowing())),
                Placeholder.component("color", text(colorName(appearance.glowColor())))));
        to.sendMessage(messages.get("command.appearance.line-scale",
                Placeholder.component("scale", text(appearance.scale() == null
                        ? null : String.valueOf(appearance.scale())))));
        to.sendMessage(messages.get("command.appearance.line-turn",
                Placeholder.component("state", appearance.turnToPlayer() == null
                        ? none() : state(appearance.turnToPlayer()))));

        if (appearance.equipment().isEmpty()) {
            to.sendMessage(messages.get("command.appearance.line-equipment-empty"));
        } else {
            for (Map.Entry<String, org.bukkit.inventory.ItemStack> entry : appearance.equipment().entrySet()) {
                to.sendMessage(messages.get("command.appearance.line-equipment",
                        Placeholder.parsed("slot", entry.getKey()),
                        Placeholder.component("item", Displays.item(entry.getValue()))));
            }
        }
        for (Map.Entry<String, String> entry : appearance.attributes().entrySet()) {
            to.sendMessage(messages.get("command.appearance.line-attribute",
                    Placeholder.parsed("name", entry.getKey()),
                    Placeholder.parsed("value", entry.getValue())));
        }
    }

    private Component text(String value) {
        return value == null || value.isBlank() ? none() : Component.text(value);
    }

    private Component state(boolean on) {
        return Component.text(messages.getRaw(on ? "action.state-on" : "action.state-off"));
    }

    private Component none() {
        return messages.get("command.appearance.none");
    }

    private static String colorName(NamedTextColor color) {
        return color == null ? null : NamedTextColor.NAMES.key(color);
    }
}

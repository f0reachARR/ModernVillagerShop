package me.f0reach.vshop.ui.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Helpers for chat-side rendering of game objects. We deliberately keep
 * Adventure-only output (no MiniMessage round-trip) so the resulting
 * Components carry hover/translatable structure intact through chat.
 */
public final class Displays {

    private Displays() {}

    /**
     * A translatable item name (so vanilla / resource-pack overrides apply per
     * client locale) with a Minecraft-style item hover. Falls back to the
     * material key when {@link ItemStack#displayName()} would throw.
     */
    public static Component item(ItemStack stack) {
        if (stack == null) return Component.text("?");
        try {
            return stack.displayName().hoverEvent(stack.asHoverEvent());
        } catch (Throwable ignored) {
            return Component.text(stack.getType().name())
                    .hoverEvent(stack.asHoverEvent());
        }
    }

    /**
     * Short 8-char form of a UUID with the full UUID exposed in a hover. Used
     * everywhere we'd otherwise truncate silently.
     */
    public static Component shortId(UUID id) {
        if (id == null) return Component.text("-");
        String full = id.toString();
        return Component.text(full.substring(0, 8))
                .hoverEvent(HoverEvent.showText(Component.text(full, NamedTextColor.GRAY)));
    }

    /**
     * A name string with the full text surfaced as a hover — useful when we
     * truncate visually but want the full value reachable.
     */
    public static Component nameWithHover(String visible, String full) {
        Component c = Component.text(visible == null ? "" : visible);
        if (full == null || full.isEmpty() || full.equals(visible)) return c;
        return c.hoverEvent(HoverEvent.showText(Component.text(full, NamedTextColor.GRAY)));
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(1, max - 1)) + "…";
    }
}

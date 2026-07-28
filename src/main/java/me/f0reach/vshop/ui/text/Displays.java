package me.f0reach.vshop.ui.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
     * client locale) with a Minecraft-style item hover. Rendered the vanilla
     * chat way — bracketed and carrying the stack's rarity style. Falls back to
     * {@link #itemName(ItemStack)} when {@link ItemStack#displayName()} would
     * throw.
     */
    public static Component item(ItemStack stack) {
        if (stack == null) return Component.text("?");
        try {
            return withItemHover(stack.displayName(), stack);
        } catch (Throwable ignored) {
            return itemName(stack);
        }
    }

    /**
     * The bare item name for embedding into a localized message: the stack's
     * custom display name when it has one, otherwise a
     * {@link Component#translatable} of the item's translation key so every
     * client renders it in its own language. The stack itself is attached as an
     * item hover, so the full tooltip stays reachable.
     *
     * <p>Unlike {@link #item(ItemStack)} this carries no brackets and no rarity
     * color, which keeps the surrounding message in control of the styling —
     * insert it with {@code Placeholder.component("item", ...)}.</p>
     */
    public static Component itemName(ItemStack stack) {
        if (stack == null) return Component.text("?");
        return withItemHover(baseName(stack), stack);
    }

    /**
     * Custom display name if the stack carries one, else a translatable of the
     * item's translation key with the material name as the client-side
     * fallback. Both lookups need the server implementation, so both are
     * guarded — the last resort is the plain material name.
     */
    private static Component baseName(ItemStack stack) {
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                Component displayName = meta.displayName();
                if (displayName != null) return displayName;
            }
        } catch (Throwable ignored) {
            // fall through to the translatable name
        }
        try {
            return Component.translatable(stack.translationKey(), stack.getType().name());
        } catch (Throwable ignored) {
            return Component.text(stack.getType().name());
        }
    }

    /** Item hover is implementation-backed; drop it rather than fail the message. */
    private static Component withItemHover(Component name, ItemStack stack) {
        try {
            return name.hoverEvent(stack.asHoverEvent());
        } catch (Throwable ignored) {
            return name;
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

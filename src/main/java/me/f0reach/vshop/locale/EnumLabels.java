package me.f0reach.vshop.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Locale;
import java.util.function.Function;

/**
 * Resolves player-facing labels for the domain enums ({@code TradeSide},
 * {@code LimitScope}, {@code CoOwnerRole}, {@code ShopType}) from the locale
 * files, so raw constant names never reach players.
 *
 * <p>Keys are derived as {@code enum.<kebab-class>.<kebab-value>} — e.g.
 * {@code enum.trade-side.sell}, {@code enum.limit-scope.per-player},
 * {@code enum.co-owner-role.primary}. A missing key falls back to
 * {@link Enum#name()} rather than the message key, so an incomplete
 * translation degrades to the previous behavior instead of leaking
 * {@code enum.trade-side.sell} into a chest lore line.</p>
 *
 * <p>Labels are intentionally plain (no color tags): call sites insert them
 * with {@code Placeholder.component}, and the surrounding message supplies the
 * color — e.g. {@code "<yellow><side></yellow>"}.</p>
 *
 * <p>This is display only. Enum names remain the wire format for the database,
 * YAML import/export, command arguments and Dialog dropdown option IDs.</p>
 */
public final class EnumLabels {

    private final Function<String, String> resolver;
    private final MiniMessage miniMessage;

    public EnumLabels(MessageManager messages) {
        this(messages::getRaw, messages.miniMessage());
    }

    /** For tests: pass a plain key-to-raw resolver instead of a live MessageManager. */
    public EnumLabels(Function<String, String> resolver, MiniMessage miniMessage) {
        this.resolver = resolver;
        this.miniMessage = miniMessage;
    }

    /** Message key for {@code value} — e.g. {@code enum.limit-scope.per-player}. */
    public static String keyFor(Enum<?> value) {
        return "enum." + kebab(value.getDeclaringClass().getSimpleName())
                + "." + value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** Localized label as raw MiniMessage, or the constant name if untranslated. */
    public String raw(Enum<?> value) {
        if (value == null) return "";
        String key = keyFor(value);
        String found = resolver.apply(key);
        return found == null || found.equals(key) ? value.name() : found;
    }

    /** Localized label, ready for {@code Placeholder.component}. */
    public Component label(Enum<?> value) {
        return miniMessage.deserialize(raw(value));
    }

    /** {@code CoOwnerRole} -> {@code co-owner-role}. */
    private static String kebab(String simpleName) {
        StringBuilder out = new StringBuilder(simpleName.length() + 4);
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('-');
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}

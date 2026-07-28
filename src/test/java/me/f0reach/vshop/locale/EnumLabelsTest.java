package me.f0reach.vshop.locale;

import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.ShopEntityKind;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.model.SkinVariant;
import me.f0reach.vshop.model.TradeSide;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnumLabelsTest {

    /** Every enum constant that {@link EnumLabels} is expected to translate. */
    private static final List<Enum<?>> ALL = List.of(
            TradeSide.SELL, TradeSide.BUY, TradeSide.BOTH,
            LimitScope.PER_PLAYER, LimitScope.GLOBAL,
            CoOwnerRole.PRIMARY, CoOwnerRole.MANAGER, CoOwnerRole.STAFF,
            ShopType.PLAYER, ShopType.ADMIN,
            ShopEntityKind.VILLAGER, ShopEntityKind.FANCY_NPC,
            SkinVariant.AUTO, SkinVariant.SLIM);

    private static EnumLabels labels(Map<String, String> table) {
        Function<String, String> resolver = k -> table.getOrDefault(k, k);
        return new EnumLabels(resolver, MiniMessage.miniMessage());
    }

    private static String plain(net.kyori.adventure.text.Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    @Test
    void derivesKebabKeyFromClassAndConstant() {
        assertEquals("enum.trade-side.sell", EnumLabels.keyFor(TradeSide.SELL));
        assertEquals("enum.trade-side.both", EnumLabels.keyFor(TradeSide.BOTH));
        assertEquals("enum.limit-scope.per-player", EnumLabels.keyFor(LimitScope.PER_PLAYER));
        assertEquals("enum.limit-scope.global", EnumLabels.keyFor(LimitScope.GLOBAL));
        assertEquals("enum.co-owner-role.primary", EnumLabels.keyFor(CoOwnerRole.PRIMARY));
        assertEquals("enum.shop-type.admin", EnumLabels.keyFor(ShopType.ADMIN));
        assertEquals("enum.shop-entity-kind.fancy-npc", EnumLabels.keyFor(ShopEntityKind.FANCY_NPC));
        assertEquals("enum.skin-variant.slim", EnumLabels.keyFor(SkinVariant.SLIM));
    }

    @Test
    void resolvesLabelFromTable() {
        EnumLabels l = labels(Map.of(
                "enum.trade-side.sell", "販売",
                "enum.limit-scope.per-player", "プレイヤーごと"));
        assertEquals("販売", l.raw(TradeSide.SELL));
        assertEquals("販売", plain(l.label(TradeSide.SELL)));
        assertEquals("プレイヤーごと", l.raw(LimitScope.PER_PLAYER));
    }

    @Test
    void missingKeyFallsBackToConstantName_notTheMessageKey() {
        EnumLabels bare = labels(Map.of());
        assertEquals("SELL", bare.raw(TradeSide.SELL));
        assertEquals("PER_PLAYER", bare.raw(LimitScope.PER_PLAYER));
        assertEquals("ADMIN", plain(bare.label(ShopType.ADMIN)));

        // A resolver that returns null (rather than echoing the key) behaves the same.
        EnumLabels nulls = new EnumLabels(k -> null, MiniMessage.miniMessage());
        assertEquals("BOTH", nulls.raw(TradeSide.BOTH));
    }

    @Test
    void nullValueRendersEmpty() {
        assertEquals("", labels(Map.of()).raw(null));
    }

    @Test
    void labelParsesMiniMessageInTheTranslation() {
        EnumLabels l = labels(Map.of("enum.shop-type.admin", "<red>Admin shop"));
        assertEquals("Admin shop", plain(l.label(ShopType.ADMIN)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ja"})
    void everyEnumConstantHasATranslation(String locale) {
        Map<String, Object> yml = loadLang(locale);
        List<String> missing = new ArrayList<>();
        for (Enum<?> value : ALL) {
            String key = EnumLabels.keyFor(value);
            Object found = lookup(yml, key);
            if (!(found instanceof String s) || s.isBlank()) missing.add(key);
        }
        assertTrue(missing.isEmpty(), "messages_" + locale + ".yml is missing " + missing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "ja"})
    void labelsCarryNoColorTags(String locale) {
        // Call sites insert labels with Placeholder.component inside an already
        // colored message; a color tag here would leak past the closing tag.
        Map<String, Object> yml = loadLang(locale);
        for (Enum<?> value : ALL) {
            String raw = (String) lookup(yml, EnumLabels.keyFor(value));
            assertNotNull(raw);
            assertEquals(-1, raw.indexOf('<'),
                    EnumLabels.keyFor(value) + " in messages_" + locale + ".yml must be plain text");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadLang(String locale) {
        String path = "lang/messages_" + locale + ".yml";
        try (InputStream in = EnumLabelsTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " not on the test classpath");
            return (Map<String, Object>) new Yaml().load(in);
        } catch (java.io.IOException ex) {
            throw new AssertionError("failed to read " + path, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object lookup(Map<String, Object> root, String dottedKey) {
        Object node = root;
        for (String part : dottedKey.split("\\.")) {
            if (!(node instanceof Map)) return null;
            node = ((Map<String, Object>) node).get(part);
        }
        return node;
    }
}

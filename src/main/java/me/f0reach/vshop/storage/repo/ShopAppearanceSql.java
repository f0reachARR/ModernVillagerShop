package me.f0reach.vshop.storage.repo;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.model.ShopEntityKind;
import me.f0reach.vshop.model.SkinVariant;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Column list, row mapping and value codecs shared by the SQLite and MySQL
 * {@link ShopAppearanceRepository} implementations. Only the upsert syntax
 * differs between the two dialects, so everything else lives here.
 *
 * <p>Unrecognised stored values (an entity type or colour removed by a
 * Minecraft update, a malformed attribute blob) decode to null/empty rather
 * than throwing: a shop must still load if part of its cosmetics went stale.
 */
public final class ShopAppearanceSql {

    public static final String COLUMNS =
            "shop_id, backend, entity_type, skin, skin_variant, glowing, glow_color, " +
                    "scale, turn_to_player, attributes, updated_at";

    /** Placeholder list matching {@link #COLUMNS}, for the INSERT clause. */
    public static final String PLACEHOLDERS = "?,?,?,?,?,?,?,?,?,?,?";

    private static final Gson GSON = new Gson();
    private static final Type STRING_MAP = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

    private ShopAppearanceSql() {}

    /** Binds the {@link #COLUMNS} values in order, starting at parameter 1. */
    public static void bindAppearance(PreparedStatement ps, ShopAppearance a) throws SQLException {
        ps.setString(1, a.shopId().toString());
        ps.setString(2, a.backend().name());
        setNullableString(ps, 3, a.entityType() == null ? null : a.entityType().getKey().toString());
        setNullableString(ps, 4, a.skin());
        setNullableString(ps, 5, a.skinVariant() == null ? null : a.skinVariant().name());
        ps.setInt(6, a.glowing() ? 1 : 0);
        setNullableString(ps, 7, encodeColor(a.glowColor()));
        if (a.scale() == null) {
            ps.setNull(8, Types.DOUBLE);
        } else {
            ps.setDouble(8, a.scale());
        }
        if (a.turnToPlayer() == null) {
            ps.setNull(9, Types.INTEGER);
        } else {
            ps.setInt(9, a.turnToPlayer() ? 1 : 0);
        }
        setNullableString(ps, 10, encodeAttributes(a.attributes()));
        Instant updated = a.updatedAt() == null ? Instant.now() : a.updatedAt();
        ps.setLong(11, updated.toEpochMilli());
    }

    /** Reads one {@code shop_appearance} row. Equipment is loaded separately. */
    public static ShopAppearance mapAppearance(ResultSet rs) throws SQLException {
        UUID shopId = UUID.fromString(rs.getString("shop_id"));
        ShopAppearance a = new ShopAppearance(shopId, decodeBackend(rs.getString("backend")));
        a.setEntityType(decodeEntityType(rs.getString("entity_type")));
        a.setSkin(rs.getString("skin"));
        a.setSkinVariant(decodeSkinVariant(rs.getString("skin_variant")));
        a.setGlowing(rs.getInt("glowing") != 0);
        a.setGlowColor(decodeColor(rs.getString("glow_color")));

        double scale = rs.getDouble("scale");
        a.setScale(rs.wasNull() ? null : (float) scale);

        int turn = rs.getInt("turn_to_player");
        a.setTurnToPlayer(rs.wasNull() ? null : turn != 0);

        a.attributes().putAll(decodeAttributes(rs.getString("attributes")));
        a.setUpdatedAt(Instant.ofEpochMilli(rs.getLong("updated_at")));
        return a;
    }

    public static String encodeAttributes(Map<String, String> attributes) {
        return attributes.isEmpty() ? null : GSON.toJson(attributes);
    }

    public static Map<String, String> decodeAttributes(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> parsed = GSON.fromJson(json, STRING_MAP);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonSyntaxException ex) {
            return Map.of();
        }
    }

    private static ShopEntityKind decodeBackend(String raw) {
        if (raw == null) return ShopEntityKind.VILLAGER;
        try {
            return ShopEntityKind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return ShopEntityKind.VILLAGER;
        }
    }

    private static SkinVariant decodeSkinVariant(String raw) {
        if (raw == null) return null;
        try {
            return SkinVariant.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static EntityType decodeEntityType(String raw) {
        if (raw == null) return null;
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(raw);
        return key == null ? null : Registry.ENTITY_TYPE.get(key);
    }

    private static String encodeColor(NamedTextColor color) {
        return color == null ? null : NamedTextColor.NAMES.key(color);
    }

    private static NamedTextColor decodeColor(String raw) {
        return raw == null ? null : NamedTextColor.NAMES.value(raw);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }
}

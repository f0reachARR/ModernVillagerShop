package me.f0reach.vshop.model;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How a shop looks in the world, over and above the shop record itself.
 *
 * <p>A shop without a row here renders as a plain Villager, so this type only
 * ever describes deviations from that default. Every field except
 * {@link #backend()} is nullable/optional and means "leave it to the backend
 * default" when unset.
 *
 * <p>Equipment slots and attribute names are plain strings on purpose: both
 * namespaces belong to FancyNpcs and change between its releases, so they are
 * validated where they are applied ({@code integration/fancynpcs}) rather than
 * mirrored into an enum here that would silently drift.
 */
public final class ShopAppearance {

    private final UUID shopId;
    private ShopEntityKind backend;
    private EntityType entityType;
    private String skin;
    private SkinVariant skinVariant;
    private boolean glowing;
    private NamedTextColor glowColor;
    private Float scale;
    private Boolean turnToPlayer;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final Map<String, ItemStack> equipment = new LinkedHashMap<>();
    private Instant updatedAt;

    public ShopAppearance(UUID shopId, ShopEntityKind backend) {
        this.shopId = shopId;
        this.backend = backend;
    }

    /** The implicit appearance of a shop with no stored row. */
    public static ShopAppearance defaultFor(UUID shopId) {
        return new ShopAppearance(shopId, ShopEntityKind.VILLAGER);
    }

    public UUID shopId() { return shopId; }
    public ShopEntityKind backend() { return backend; }
    public EntityType entityType() { return entityType; }
    public String skin() { return skin; }
    public SkinVariant skinVariant() { return skinVariant; }
    public boolean glowing() { return glowing; }
    public NamedTextColor glowColor() { return glowColor; }
    public Float scale() { return scale; }
    public Boolean turnToPlayer() { return turnToPlayer; }
    public Instant updatedAt() { return updatedAt; }

    /** Live view of the FancyNpcs attribute overrides, keyed by attribute name. */
    public Map<String, String> attributes() { return attributes; }

    /** Live view of the equipment, keyed by FancyNpcs equipment-slot name. */
    public Map<String, ItemStack> equipment() { return equipment; }

    public boolean isDefault() {
        return backend == ShopEntityKind.VILLAGER
                && entityType == null && skin == null && skinVariant == null
                && !glowing && glowColor == null && scale == null && turnToPlayer == null
                && attributes.isEmpty() && equipment.isEmpty();
    }

    public void setBackend(ShopEntityKind backend) { this.backend = backend; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }
    public void setSkin(String skin) { this.skin = skin; }
    public void setSkinVariant(SkinVariant skinVariant) { this.skinVariant = skinVariant; }
    public void setGlowing(boolean glowing) { this.glowing = glowing; }
    public void setGlowColor(NamedTextColor glowColor) { this.glowColor = glowColor; }
    public void setScale(Float scale) { this.scale = scale; }
    public void setTurnToPlayer(Boolean turnToPlayer) { this.turnToPlayer = turnToPlayer; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

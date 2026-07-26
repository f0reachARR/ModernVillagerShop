package me.f0reach.vshop.model;

/**
 * Which in-world representation backs a shop. Persisted by name in
 * {@code shop_appearance.backend} and resolved by
 * {@code shop.entity.ShopEntityService}.
 */
public enum ShopEntityKind {
    /** A real, AI-disabled Bukkit Villager. The default and the only fallback. */
    VILLAGER,
    /** A packet-based FancyNpcs NPC. Requires the FancyNpcs plugin to be present. */
    FANCY_NPC
}

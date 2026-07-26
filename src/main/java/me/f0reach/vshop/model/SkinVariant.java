package me.f0reach.vshop.model;

/**
 * Arm model of a player skin. Mirrors the two values FancyNpcs accepts —
 * there is no explicit CLASSIC: {@link #AUTO} lets the skin's own metadata
 * decide, which is classic for most skins.
 */
public enum SkinVariant {
    AUTO,
    SLIM
}

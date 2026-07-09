package me.f0reach.vshop.sound;

import org.bukkit.SoundCategory;

/**
 * Parsed sound specification for a single event. {@code key} is the fully
 * qualified namespaced sound identifier (e.g. {@code minecraft:ui.button.click})
 * — validated for format at parse time but passed as a string to
 * {@link org.bukkit.entity.Player#playSound(org.bukkit.Location, String,
 * SoundCategory, float, float)} so unknown-to-server keys degrade silently on
 * the client rather than blocking plugin startup.
 */
record SoundSpec(String key, SoundCategory category, float volume, float pitch) {}

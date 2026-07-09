package me.f0reach.vshop.sound;

import me.f0reach.vshop.config.PluginConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Plays configured feedback sounds to a single player. Config is parsed once
 * per reload into a validated map; malformed namespaced keys are logged there
 * so we don't spam warnings on every play. Callers reference events by the
 * {@link SoundEvents} constants — an unknown event key is a silent no-op so
 * feature callers never need a null check.
 *
 * <p>Sounds are always played via {@link Player#playSound(org.bukkit.Location,
 * String, SoundCategory, float, float)} at the player's own location. This is
 * only audible to that player, never to nearby players. The string variant is
 * intentional: sounds unknown to the server registry (custom resource packs,
 * datapack sounds) still ship to the client and degrade silently if the
 * client's own registry doesn't know them either.</p>
 */
public final class SoundService {

    private final PluginConfig config;
    private final Logger logger;
    private volatile Map<String, SoundSpec> specs = Map.of();

    public SoundService(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        reload();
    }

    /**
     * Re-parses the {@code sounds:} section from the current {@link PluginConfig}
     * snapshot. Called from the composition root after
     * {@code PluginConfig.reload(...)} so {@code /vshop reload} refreshes both
     * in lockstep.
     */
    public void reload() {
        ConfigurationSection root = config.soundsSection();
        Map<String, SoundSpec> next = new HashMap<>();
        if (root != null) {
            for (String eventKey : SoundEvents.ALL) {
                // getConfigurationSection walks the dotted path — matches the
                // nested YAML shape (sounds.ui.open, sounds.trade.success.sell, ...).
                ConfigurationSection sec = root.getConfigurationSection(eventKey);
                if (sec == null) continue;
                String rawKey = sec.getString("key", "");
                if (rawKey == null || rawKey.isBlank()) continue;

                NamespacedKey nk = NamespacedKey.fromString(rawKey);
                if (nk == null) {
                    logger.warning("sounds." + eventKey + ".key is not a valid namespaced key: " + rawKey);
                    continue;
                }

                SoundCategory category = parseCategory(sec.getString("category", "MASTER"), eventKey);
                float volume = (float) sec.getDouble("volume", 1.0);
                float pitch = (float) sec.getDouble("pitch", 1.0);
                next.put(eventKey, new SoundSpec(nk.asString(), category, volume, pitch));
            }
        }
        this.specs = Map.copyOf(next);
    }

    /**
     * Plays the sound bound to {@code eventKey} to {@code player} alone.
     * No-op if the player is null, the event is unconfigured, or the config
     * entry was omitted (empty key / invalid / unknown sound).
     */
    public void play(Player player, String eventKey) {
        if (player == null) return;
        SoundSpec spec = specs.get(eventKey);
        if (spec == null) return;
        player.playSound(player.getLocation(), spec.key(), spec.category(), spec.volume(), spec.pitch());
    }

    private SoundCategory parseCategory(String raw, String eventKey) {
        if (raw == null) return SoundCategory.MASTER;
        try {
            return SoundCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            logger.warning("sounds." + eventKey + ".category is not a valid SoundCategory: " + raw
                    + " (falling back to MASTER)");
            return SoundCategory.MASTER;
        }
    }
}

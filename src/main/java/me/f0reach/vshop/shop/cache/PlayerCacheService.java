package me.f0reach.vshop.shop.cache;

import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.model.PlayerCacheEntry;
import me.f0reach.vshop.storage.repo.PlayerCacheRepository;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores recently-seen players for the picker UI. Reads/writes happen async on
 * Bukkit's scheduler so chat/world ticks don't block on the DB.
 */
public final class PlayerCacheService {

    private static final Logger LOG = Logger.getLogger(PlayerCacheService.class.getName());

    private final ModernVillagerShopPlugin plugin;
    private final PlayerCacheRepository repo;
    private final PluginConfig config;

    public PlayerCacheService(ModernVillagerShopPlugin plugin) {
        this.plugin = plugin;
        this.repo = plugin.storage().playerCache();
        this.config = plugin.pluginConfig();
    }

    /** Fire-and-forget upsert from a join/quit/seen event. */
    public void touchAsync(Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final String texValue;
        final String texSig;
        if (shouldRefreshTexture(player)) {
            String[] tex = readTexture(player.getPlayerProfile());
            texValue = tex[0];
            texSig = tex[1];
        } else {
            texValue = null;
            texSig = null;
        }
        Instant now = Instant.now();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                repo.upsert(new PlayerCacheEntry(
                        uuid, name, name.toLowerCase(Locale.ROOT),
                        texValue, texSig,
                        texValue == null ? null : now,
                        now, now));
            } catch (SQLException ex) {
                LOG.log(Level.WARNING, "PlayerCache upsert failed for " + name, ex);
            }
        });
    }

    public Optional<PlayerCacheEntry> findByUuid(UUID id) {
        try { return repo.findByUuid(id); }
        catch (SQLException ex) {
            LOG.warning("findByUuid failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<PlayerCacheEntry> findByName(String name) {
        try { return repo.findByName(name); }
        catch (SQLException ex) {
            LOG.warning("findByName failed: " + ex.getMessage());
            return Optional.empty();
        }
    }

    public java.util.List<PlayerCacheEntry> page(int offset, int limit, boolean byName) {
        try { return repo.page(offset, limit, byName); }
        catch (SQLException ex) {
            LOG.warning("page failed: " + ex.getMessage());
            return java.util.List.of();
        }
    }

    public java.util.List<PlayerCacheEntry> search(String query, int limit) {
        try { return repo.search(query, limit); }
        catch (SQLException ex) {
            LOG.warning("search failed: " + ex.getMessage());
            return java.util.List.of();
        }
    }

    private boolean shouldRefreshTexture(Player player) {
        try {
            var existing = repo.findByUuid(player.getUniqueId()).orElse(null);
            if (existing == null) return true;
            if (existing.textureUpdatedAt() == null) return true;
            return existing.textureUpdatedAt().isBefore(
                    Instant.now().minus(config.playerCache().textureTtl()));
        } catch (SQLException ex) {
            return true;
        }
    }

    /** Best-effort extraction of [value, signature] from the player profile. */
    @SuppressWarnings("deprecation")
    private static String[] readTexture(PlayerProfile profile) {
        try {
            PlayerTextures textures = profile.getTextures();
            URL skinUrl = textures == null ? null : textures.getSkin();
            return skinUrl == null ? new String[]{null, null} : new String[]{skinUrl.toString(), null};
        } catch (Throwable t) {
            return new String[]{null, null};
        }
    }
}

package me.f0reach.vshop.shop.listener;

import me.f0reach.vshop.shop.cache.PlayerCacheService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Records each known player into {@code player_cache} on join/quit so the
 * player picker UI can render heads without spending a profile lookup.
 */
public final class PlayerCacheListener implements Listener {

    private final PlayerCacheService cache;

    public PlayerCacheListener(PlayerCacheService cache) {
        this.cache = cache;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cache.touchAsync(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.touchAsync(event.getPlayer());
    }
}

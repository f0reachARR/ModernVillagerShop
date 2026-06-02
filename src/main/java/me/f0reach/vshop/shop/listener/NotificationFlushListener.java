package me.f0reach.vshop.shop.listener;

import me.f0reach.vshop.shop.trade.TradeNotifier;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Flushes any queued offline trade notifications shortly after a player joins.
 * A 2-second delay gives the player a moment to settle in before chat lights up.
 */
public final class NotificationFlushListener implements Listener {

    private final Plugin plugin;
    private final TradeNotifier notifier;

    public NotificationFlushListener(Plugin plugin, TradeNotifier notifier) {
        this.plugin = plugin;
        this.notifier = notifier;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> notifier.flushPending(event.getPlayer()), 40L);
    }
}

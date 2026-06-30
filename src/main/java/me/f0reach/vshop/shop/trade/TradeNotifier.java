package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.economy.EconomyService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Dispatches trade notifications: online owners receive an immediate chat
 * message; the trade-service queues offline owners' notices in
 * {@code shop_notifications}, which we flush on {@link
 * me.f0reach.vshop.shop.listener.NotificationFlushListener PlayerJoinEvent}.
 */
public final class TradeNotifier {

    private final Plugin plugin;
    private final MessageManager messages;
    private final StorageManager storage;
    private final EconomyService economy;

    public TradeNotifier(Plugin plugin, MessageManager messages, StorageManager storage, EconomyService economy) {
        this.plugin = plugin;
        this.messages = messages;
        this.storage = storage;
        this.economy = economy;
    }

    public void notifyOnline(List<CoOwner> coOwners, Shop shop, TradeSide side, int amount, BigDecimal value,
                             ItemStack item) {
        for (CoOwner co : coOwners) {
            if (!co.role().receivesNotifications()) continue;
            Player p = Bukkit.getPlayer(co.playerUuid());
            if (p == null) continue;
            if (!wantsNotifications(co.playerUuid())) continue;
            Component msg = messages.get(
                    side == TradeSide.SELL ? "trade.notify-sell" : "trade.notify-buy",
                    Placeholder.parsed("shop_name", shop.name()),
                    Placeholder.parsed("amount", Integer.toString(amount)),
                    Placeholder.parsed("price", economy.format(value)),
                    Placeholder.parsed("item", itemName(item))
            );
            p.sendMessage(msg);
        }
    }

    /**
     * Reads the player_preferences row for {@code uuid}. Defaults to {@code true}
     * if the row is missing or the lookup fails — better to over-notify than to
     * silently drop trade events.
     */
    private boolean wantsNotifications(java.util.UUID uuid) {
        if (storage == null) return true;
        try {
            return storage.playerPreferences().wantsNotifications(uuid);
        } catch (SQLException ex) {
            return true;
        }
    }

    /**
     * Flush queued offline notifications for the given player. Called from the
     * join listener after a short delay so chat shows up cleanly.
     */
    public void flushPending(Player player) {
        if (storage == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var pending = storage.notifications().findUndelivered(player.getUniqueId());
                if (pending.isEmpty()) return;
                int total = 0;
                BigDecimal totalValue = BigDecimal.ZERO;
                java.util.List<Long> ids = new java.util.ArrayList<>(pending.size());
                for (var p : pending) {
                    total += p.count();
                    if (p.totalAmount() != null) totalValue = totalValue.add(p.totalAmount());
                    ids.add(p.id());
                }
                storage.notifications().markDelivered(ids);
                final int totalF = total;
                final BigDecimal totalValueF = totalValue;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(messages.get("trade.notify-summary",
                            Placeholder.parsed("count", Integer.toString(totalF)),
                            Placeholder.parsed("price", economy.format(totalValueF))));
                });
            } catch (SQLException ignored) {}
        });
    }

    private String itemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item == null ? "?" : item.getType().name();
        var meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            var dn = meta.displayName();
            if (dn != null) return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(dn);
        }
        return item.getType().name();
    }
}

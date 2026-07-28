package me.f0reach.vshop.command;

import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.locale.EnumLabels;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Shared dependencies and helpers for the per-subcommand classes under
 * {@code me.f0reach.vshop.command.sub}.
 */
public final class CommandSupport {

    private final ModernVillagerShopPlugin plugin;
    private final MessageManager messages;
    private final EnumLabels enumLabels;

    public CommandSupport(ModernVillagerShopPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
        this.enumLabels = new EnumLabels(messages);
    }

    public ModernVillagerShopPlugin plugin() { return plugin; }

    public MessageManager messages() { return messages; }

    public EnumLabels enumLabels() { return enumLabels; }

    /** Resolves a shop by either an 8-char ID prefix or a full UUID. */
    public Shop findShopByPrefix(String prefix) {
        for (Shop s : plugin.registry().all()) {
            if (s.id().toString().startsWith(prefix)) return s;
        }
        try {
            return plugin.registry().byId(UUID.fromString(prefix)).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public void sendShopNotFound(CommandSender sender, String prefix) {
        sender.sendMessage(messages.get("command.shop-not-found",
                Placeholder.parsed("shop_id", prefix)));
    }

    public void sendPlayerOnly(CommandSender sender) {
        sender.sendMessage(messages.get("command.player-only"));
    }

    public void sendGenericError(CommandSender sender, Throwable ex) {
        sender.sendMessage(messages.get("error.generic",
                Placeholder.parsed("reason", String.valueOf(ex.getMessage()))));
    }

    /**
     * Resolves the admin/player shop the given player is currently looking at,
     * within {@code maxDistance} blocks. Handles both backends — see
     * {@link me.f0reach.vshop.shop.entity.ShopTargeting}, which NPC-backed shops
     * need because they are invisible to the server-side entity raycast.
     */
    public Optional<Shop> findShopFromLineOfSight(Player player, int maxDistance) {
        return plugin.shopTargeting().findFromLineOfSight(player, maxDistance);
    }
}

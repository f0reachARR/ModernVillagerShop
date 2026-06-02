package me.f0reach.vshop.shop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.chest.ShopBrowseUi;
import org.bukkit.entity.Player;

/**
 * Validates "may this player open this shop?" against permissions, distance,
 * and shop state, then hands off to the browse UI.
 *
 * Permission priority (per spec §4):
 *   open.any  >  open.<shopId>  >  open.nearby
 */
public final class ShopOpenService {

    private final ShopBrowseUi browseUi;
    private final MessageManager messages;
    private final PluginConfig config;

    public ShopOpenService(ShopBrowseUi browseUi, MessageManager messages, PluginConfig config) {
        this.browseUi = browseUi;
        this.messages = messages;
        this.config = config;
    }

    public void open(Player viewer, Shop shop) {
        if (shop.suspended()) {
            viewer.sendMessage(messages.get("shop.open.suspended"));
            return;
        }
        if (!hasOpenPermission(viewer, shop)) {
            viewer.sendMessage(messages.get("command.no-permission"));
            return;
        }
        if (!withinOpenDistance(viewer, shop)) {
            viewer.sendMessage(messages.get("shop.open.too-far"));
            return;
        }
        browseUi.open(viewer, shop, 0);
    }

    private boolean hasOpenPermission(Player player, Shop shop) {
        if (!player.hasPermission("modernvillagershop.use")) return false;
        if (player.hasPermission("modernvillagershop.open.any")) return true;
        if (player.hasPermission("modernvillagershop.open." + shop.id().toString())) return true;
        // Default: nearby (which we also re-check distance-wise below).
        return player.hasPermission("modernvillagershop.open.nearby");
    }

    private boolean withinOpenDistance(Player player, Shop shop) {
        if (player.hasPermission("modernvillagershop.open.any")) return true;
        if (player.hasPermission("modernvillagershop.open." + shop.id().toString())) return true;
        var loc = shop.location().toBukkit();
        if (loc == null || !loc.getWorld().equals(player.getWorld())) return false;
        double maxSq = config.shop().openDistance() * config.shop().openDistance();
        return loc.distanceSquared(player.getLocation()) <= maxSq;
    }
}

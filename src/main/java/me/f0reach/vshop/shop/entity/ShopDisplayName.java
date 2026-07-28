package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Renders the floating name above a shop from {@code shop.villagerNameFormat}.
 *
 * <p>Two outputs because the two backends want different things: Bukkit takes a
 * built {@link Component}, while FancyNpcs takes a MiniMessage string that it
 * parses itself.
 */
public final class ShopDisplayName {

    private final Plugin plugin;
    private final MessageManager messages;
    private final CoOwnerRepository coOwnerRepo;
    private final PluginConfig config;

    public ShopDisplayName(Plugin plugin, MessageManager messages, CoOwnerRepository coOwnerRepo,
                           PluginConfig config) {
        this.plugin = plugin;
        this.messages = messages;
        this.coOwnerRepo = coOwnerRepo;
        this.config = config;
    }

    public Component component(Shop shop) {
        return messages.miniMessage().deserialize(format(shop),
                Placeholder.parsed("shop_name", shopName(shop)),
                Placeholder.parsed("primary", primaryName(shop)));
    }

    /** MiniMessage source with the placeholders substituted, left unparsed. */
    public String miniMessage(Shop shop) {
        return format(shop)
                .replace("<shop_name>", shopName(shop))
                .replace("<primary>", primaryName(shop));
    }

    private String format(Shop shop) {
        return shop.isAdminShop()
                ? config.shop().villagerNameFormatAdmin()
                : config.shop().villagerNameFormat();
    }

    private static String shopName(Shop shop) {
        return shop.name() == null ? "" : shop.name();
    }

    private String primaryName(Shop shop) {
        if (shop.isAdminShop()) return "";
        UUID owner = shop.ownerUuid();
        if (owner == null) {
            // Fall back to scanning the co-owner table (e.g. cache miss).
            try {
                for (CoOwner co : coOwnerRepo.findByShop(shop.id())) {
                    if (co.role().canDeleteShop()) {
                        owner = co.playerUuid();
                        break;
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("Failed to resolve PRIMARY for shop " + shop.id() + ": " + ex.getMessage());
            }
        }
        if (owner == null) return "";
        OfflinePlayer op = Bukkit.getOfflinePlayer(owner);
        return op.getName() == null ? owner.toString().substring(0, 8) : op.getName();
    }
}

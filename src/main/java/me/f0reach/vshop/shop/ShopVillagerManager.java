package me.f0reach.vshop.shop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Spawns / refreshes / removes the Villager entity that represents a shop and
 * keeps its appearance (custom name, AI-disabled, invulnerable) in sync with
 * the shop record.
 */
public final class ShopVillagerManager {

    public static final String VILLAGER_PDC_KEY = "shop_id";

    private final Plugin plugin;
    private final MessageManager messages;
    private final CoOwnerRepository coOwnerRepo;
    private final org.bukkit.NamespacedKey villagerKey;

    public ShopVillagerManager(Plugin plugin, MessageManager messages, CoOwnerRepository coOwnerRepo) {
        this.plugin = plugin;
        this.messages = messages;
        this.coOwnerRepo = coOwnerRepo;
        this.villagerKey = new org.bukkit.NamespacedKey(plugin, VILLAGER_PDC_KEY);
    }

    public org.bukkit.NamespacedKey villagerKey() {
        return villagerKey;
    }

    /**
     * Spawns the villager for a shop and returns its UUID. The caller is
     * responsible for persisting the UUID on the {@link Shop}.
     */
    public UUID spawn(Shop shop, Location at, PluginConfig config) {
        Villager villager = at.getWorld().spawn(at, Villager.class, v -> applyAttributes(v, shop, config));
        return villager.getUniqueId();
    }

    /**
     * Re-applies attributes to an already-spawned villager. Useful after a
     * profession or name change, or after the shop's PRIMARY changes.
     */
    public void refresh(Villager villager, Shop shop, PluginConfig config) {
        applyAttributes(villager, shop, config);
    }

    private void applyAttributes(Villager v, Shop shop, PluginConfig config) {
        v.setAI(false);
        v.setInvulnerable(true);
        v.setRemoveWhenFarAway(false);
        v.setPersistent(true);
        v.setCollidable(false);
        v.setCanPickupItems(false);
        var maxHealth = v.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(20.0);
            v.setHealth(maxHealth.getValue());
        }

        if (shop.profession() != null) {
            v.setProfession(shop.profession());
        }
        // Mark this villager as belonging to a shop for fast event-side lookup.
        v.getPersistentDataContainer().set(villagerKey, PersistentDataType.STRING, shop.id().toString());

        v.customName(buildName(shop, config));
        v.setCustomNameVisible(true);
    }

    public Component buildName(Shop shop, PluginConfig config) {
        String primaryName = shop.isAdminShop() ? "" : resolvePrimaryName(shop);
        String format = config.shop().villagerNameFormat();
        return messages.miniMessage().deserialize(format,
                Placeholder.parsed("shop_name", shop.name() == null ? "" : shop.name()),
                Placeholder.parsed("primary", primaryName));
    }

    private String resolvePrimaryName(Shop shop) {
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

    /**
     * Returns the shop villager located at the same UUID as the one persisted
     * on the shop record, or null if not loaded.
     */
    public Villager findEntity(Shop shop) {
        if (shop.villagerEntityId() == null) return null;
        if (shop.location().toBukkit() == null) return null;
        var entity = Bukkit.getEntity(shop.villagerEntityId());
        if (entity instanceof Villager v) return v;
        return null;
    }

    public void remove(Shop shop) {
        Villager v = findEntity(shop);
        if (v != null) v.remove();
    }
}

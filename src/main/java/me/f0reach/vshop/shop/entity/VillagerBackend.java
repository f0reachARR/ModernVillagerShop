package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Backs a shop with a real, AI-disabled, invulnerable {@link Villager} and keeps
 * its appearance (custom name, profession, flags) in sync with the shop record.
 */
public final class VillagerBackend implements ShopEntityBackend {

    public static final String VILLAGER_PDC_KEY = "shop_id";

    private final Plugin plugin;
    private final MessageManager messages;
    private final CoOwnerRepository coOwnerRepo;
    private final PluginConfig config;
    private final NamespacedKey villagerKey;

    public VillagerBackend(Plugin plugin, MessageManager messages, CoOwnerRepository coOwnerRepo,
                           PluginConfig config) {
        this.plugin = plugin;
        this.messages = messages;
        this.coOwnerRepo = coOwnerRepo;
        this.config = config;
        this.villagerKey = new NamespacedKey(plugin, VILLAGER_PDC_KEY);
    }

    /** PDC key stamped on every shop villager, for fast event-side identification. */
    public NamespacedKey villagerKey() {
        return villagerKey;
    }

    @Override
    public UUID spawn(Shop shop, Location at) {
        Villager villager = at.getWorld().spawn(at, Villager.class, v -> applyAttributes(v, shop));
        return villager.getUniqueId();
    }

    @Override
    public void refresh(Shop shop) {
        Villager v = findEntity(shop);
        if (v == null) return;
        applyAttributes(v, shop);
    }

    @Override
    public void refreshDisplayName(Shop shop) {
        // Best-effort: if the villager isn't currently loaded, the next chunk-load
        // will pick up the change via spawn().
        Villager v = findEntity(shop);
        if (v == null) return;
        v.customName(buildName(shop));
        v.setCustomNameVisible(true);
    }

    @Override
    public void remove(Shop shop) {
        Villager v = findEntity(shop);
        if (v != null) v.remove();
    }

    private void applyAttributes(Villager v, Shop shop) {
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

        v.customName(buildName(shop));
        v.setCustomNameVisible(true);
    }

    public Component buildName(Shop shop) {
        String primaryName = shop.isAdminShop() ? "" : resolvePrimaryName(shop);
        String format = shop.isAdminShop()
                ? config.shop().villagerNameFormatAdmin()
                : config.shop().villagerNameFormat();
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
     * Returns the shop villager matching the entity id persisted on the shop
     * record, or null if it is not currently loaded.
     */
    public Villager findEntity(Shop shop) {
        if (shop.villagerEntityId() == null) return null;
        if (shop.location().toBukkit() == null) return null;
        var entity = Bukkit.getEntity(shop.villagerEntityId());
        if (entity instanceof Villager v) return v;
        return null;
    }
}

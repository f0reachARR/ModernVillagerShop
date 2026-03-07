package me.f0reach.vshop.listener;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.SpawnEggManager;
import me.f0reach.vshop.ui.UIManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ShopListener implements Listener {
    private final JavaPlugin plugin;
    private final ShopService shopService;
    private final SpawnEggManager eggManager;
    private final UIManager uiManager;
    private final MessageManager messages;

    // Pending shop creation: maps player UUID to the shop type from the used egg
    private final Map<UUID, ShopType> pendingShopCreation = new ConcurrentHashMap<>();

    public ShopListener(JavaPlugin plugin, ShopService shopService, SpawnEggManager eggManager,
                        UIManager uiManager, MessageManager messages) {
        this.plugin = plugin;
        this.shopService = shopService;
        this.eggManager = eggManager;
        this.uiManager = uiManager;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager villager)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("modernvillagershop.use")) return;

        try {
            Optional<Shop> shopOpt = shopService.getShopByVillager(villager.getUniqueId());
            if (shopOpt.isPresent()) {
                event.setCancelled(true);
                Shop shop = shopOpt.get();
                if (shop.active()) {
                    uiManager.openShopInventory(player, shop);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to lookup shop for villager", e);
            player.sendMessage(messages.get("error.storage"));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !eggManager.isShopEgg(item)) return;

        Player player = event.getPlayer();
        ShopType shopType = eggManager.getShopType(item);
        boolean hasPermission = switch (shopType) {
            case ADMIN -> player.hasPermission("modernvillagershop.admin");
            case PLAYER -> player.hasPermission("modernvillagershop.create");
        };
        if (!hasPermission) {
            player.sendMessage(messages.get("error.no_permission"));
            event.setCancelled(true);
            return;
        }

        pendingShopCreation.put(player.getUniqueId(), shopType);
        // Let the event proceed so the villager spawns
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled()) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return;
        if (!(event.getEntity() instanceof Villager villager)) return;

        // Find which player spawned this - check pending
        // The spawn should happen right after the interact event
        Player spawner = null;
        ShopType pendingType = null;
        for (Player p : villager.getWorld().getPlayers()) {
            ShopType type = pendingShopCreation.remove(p.getUniqueId());
            if (type != null) {
                if (p.getLocation().distanceSquared(villager.getLocation()) < 100) {
                    spawner = p;
                    pendingType = type;
                    break;
                }
            }
        }

        if (spawner == null || pendingType == null) return;

        // Disable AI
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);

        try {
            int shopId;
            if (pendingType == ShopType.ADMIN) {
                shopId = shopService.createAdminShop(
                        villager.getUniqueId(),
                        villager.getWorld().getName(),
                        villager.getLocation().getX(),
                        villager.getLocation().getY(),
                        villager.getLocation().getZ()
                );
                spawner.sendMessage(messages.get("shop.created_admin",
                        Placeholder.unparsed("shop_id", String.valueOf(shopId))));
            } else {
                shopId = shopService.createPlayerShop(
                        villager.getUniqueId(),
                        spawner.getUniqueId(),
                        villager.getWorld().getName(),
                        villager.getLocation().getX(),
                        villager.getLocation().getY(),
                        villager.getLocation().getZ()
                );
                spawner.sendMessage(messages.get("shop.created_player",
                        Placeholder.unparsed("shop_id", String.valueOf(shopId))));
                uiManager.openShopInitDialog(spawner, shopId);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create shop from spawn egg", e);
            spawner.sendMessage(messages.get("error.storage"));
            villager.remove();
        }
    }
}

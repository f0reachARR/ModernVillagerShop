package me.f0reach.vshop.listener;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.storage.ShopRepository;
import org.bukkit.Chunk;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class VillagerProtectListener implements Listener {
    private final JavaPlugin plugin;
    private final ShopRepository shopRepo;
    private final Set<UUID> shopVillagerUuids = new HashSet<>();

    public VillagerProtectListener(JavaPlugin plugin, ShopRepository shopRepo) {
        this.plugin = plugin;
        this.shopRepo = shopRepo;
    }

    /**
     * Load all shop villager UUIDs from DB and apply AI disable to loaded entities.
     */
    public void initOnStartup() {
        try {
            List<Shop> shops = shopRepo.findAll();
            for (Shop shop : shops) {
                shopVillagerUuids.add(shop.villagerUuid());
            }
            plugin.getLogger().info("Loaded " + shopVillagerUuids.size() + " shop villager UUIDs");

            // Apply to all currently loaded entities
            for (var world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Villager villager && shopVillagerUuids.contains(villager.getUniqueId())) {
                        applyProtection(villager);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load shop villagers for protection", e);
        }
    }

    public void registerVillager(UUID villagerUuid) {
        shopVillagerUuids.add(villagerUuid);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Villager villager && shopVillagerUuids.contains(villager.getUniqueId())) {
                applyProtection(villager);
            }
        }
    }

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (shopVillagerUuids.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void applyProtection(Villager villager) {
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
    }
}

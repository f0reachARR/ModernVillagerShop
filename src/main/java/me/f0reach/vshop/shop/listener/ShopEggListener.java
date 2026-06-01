package me.f0reach.vshop.shop.listener;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.egg.SpawnEggFactory;
import me.f0reach.vshop.shop.egg.SpawnEggMeta;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Handles right-click of the shop spawn egg. Validates PDC, distance and
 * permission, then delegates to {@link ShopService#createFromEgg}. Cancels the
 * event so the vanilla egg does not also spawn a regular villager.
 */
public final class ShopEggListener implements Listener {

    private final Plugin plugin;
    private final SpawnEggFactory eggFactory;
    private final ShopService shops;
    private final MessageManager messages;

    public ShopEggListener(Plugin plugin, SpawnEggFactory eggFactory, ShopService shops, MessageManager messages) {
        this.plugin = plugin;
        this.eggFactory = eggFactory;
        this.shops = shops;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        Optional<SpawnEggMeta> metaOpt = eggFactory.read(item);
        if (metaOpt.isEmpty()) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        // Take over the event regardless of outcome so vanilla doesn't spawn a villager.
        event.setCancelled(true);

        Player player = event.getPlayer();
        SpawnEggMeta meta = metaOpt.get();

        // Permission check.
        String perm = meta.admin() ? "modernvillagershop.admin.egg" : "modernvillagershop.egg";
        if (!player.hasPermission(perm)) {
            player.sendMessage(messages.get("command.no-permission"));
            return;
        }

        BlockFace face = event.getBlockFace();
        Location placeAt = clicked.getRelative(face).getLocation().add(0.5, 0, 0.5);
        placeAt.setYaw(player.getLocation().getYaw());
        placeAt.setPitch(0f);

        try {
            shops.createFromEgg(player, placeAt, meta);
            player.sendMessage(messages.get("shop.place.placed"));
            if (player.getGameMode() != GameMode.CREATIVE) {
                consume(item, player);
            }
        } catch (ShopService.CreateException ex) {
            player.sendMessage(messages.get(ex.messageKey()));
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create shop", ex);
            player.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", "storage error")));
        }
    }

    private static void consume(ItemStack stack, Player player) {
        int n = stack.getAmount();
        if (n <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            stack.setAmount(n - 1);
        }
    }
}

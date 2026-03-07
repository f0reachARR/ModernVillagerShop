package me.f0reach.vshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.config.PluginConfig;
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

import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;

@SuppressWarnings("UnstableApiUsage")
public final class VShopCommands {
    private final PluginConfig config;
    private final MessageManager messages;
    private final ShopService shopService;
    private final SpawnEggManager eggManager;
    private final UIManager uiManager;
    private final Runnable reloadAction;

    public VShopCommands(PluginConfig config, MessageManager messages, ShopService shopService,
                         SpawnEggManager eggManager, UIManager uiManager, Runnable reloadAction) {
        this.config = config;
        this.messages = messages;
        this.shopService = shopService;
        this.eggManager = eggManager;
        this.uiManager = uiManager;
        this.reloadAction = reloadAction;
    }

    public LiteralCommandNode<CommandSourceStack> buildCommandTree() {
        return Commands.literal("vshop")
                .then(Commands.literal("open")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.use"))
                        .executes(ctx -> executeOpen(ctx.getSource()))
                )
                .then(Commands.literal("egg")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.create"))
                        .executes(ctx -> executeEgg(ctx.getSource()))
                )
                .then(Commands.literal("admin")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.admin"))
                        .then(Commands.literal("create")
                                .executes(ctx -> executeAdminCreate(ctx.getSource()))
                        )
                        .then(Commands.literal("egg")
                                .executes(ctx -> executeAdminEgg(ctx.getSource()))
                        )
                        .then(Commands.literal("edit")
                                .then(Commands.argument("shopId", IntegerArgumentType.integer(1))
                                        .executes(ctx -> executeAdminEdit(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "shopId")))
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.reload"))
                        .executes(ctx -> executeReload(ctx.getSource()))
                )
                .build();
    }

    private int executeOpen(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage(messages.get("error.player_only"));
            return Command.SINGLE_SUCCESS;
        }

        // Find the villager the player is looking at
        Entity target = player.getTargetEntity(5);
        if (!(target instanceof Villager villager)) {
            player.sendMessage(messages.get("error.look_at_villager"));
            return Command.SINGLE_SUCCESS;
        }

        try {
            Optional<Shop> shopOpt = shopService.getShopByVillager(villager.getUniqueId());
            if (shopOpt.isEmpty()) {
                player.sendMessage(messages.get("error.shop_not_found"));
                return Command.SINGLE_SUCCESS;
            }
            uiManager.openShopInventory(player, shopOpt.get());
        } catch (SQLException e) {
            source.getSender().getServer().getLogger().log(Level.SEVERE, "Failed to open shop", e);
            player.sendMessage(messages.get("error.storage"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeEgg(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage(messages.get("error.player_only"));
            return Command.SINGLE_SUCCESS;
        }

        player.getInventory().addItem(eggManager.createShopEgg());
        player.sendMessage(messages.get("shop.egg_given"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeAdminCreate(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage(messages.get("error.player_only"));
            return Command.SINGLE_SUCCESS;
        }

        Entity target = player.getTargetEntity(5);
        if (!(target instanceof Villager villager)) {
            player.sendMessage(messages.get("error.look_at_villager"));
            return Command.SINGLE_SUCCESS;
        }

        // Disable AI
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);

        try {
            int shopId = shopService.createAdminShop(
                    villager.getUniqueId(),
                    villager.getWorld().getName(),
                    villager.getLocation().getX(),
                    villager.getLocation().getY(),
                    villager.getLocation().getZ()
            );
            player.sendMessage(messages.get("shop.created_admin",
                    Placeholder.unparsed("shop_id", String.valueOf(shopId))));
        } catch (SQLException e) {
            source.getSender().getServer().getLogger().log(Level.SEVERE, "Failed to create admin shop", e);
            player.sendMessage(messages.get("error.storage"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeAdminEgg(CommandSourceStack source) {
        Entity executor = source.getExecutor();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage(messages.get("error.player_only"));
            return Command.SINGLE_SUCCESS;
        }

        player.getInventory().addItem(eggManager.createAdminShopEgg());
        player.sendMessage(messages.get("shop.admin_egg_given"));
        return Command.SINGLE_SUCCESS;
    }

    private int executeAdminEdit(CommandSourceStack source, int shopId) {
        Entity executor = source.getExecutor();
        if (!(executor instanceof Player player)) {
            source.getSender().sendMessage(messages.get("error.player_only"));
            return Command.SINGLE_SUCCESS;
        }

        try {
            Optional<Shop> shopOpt = shopService.getShopById(shopId);
            if (shopOpt.isEmpty()) {
                player.sendMessage(messages.get("error.shop_not_found"));
                return Command.SINGLE_SUCCESS;
            }
            uiManager.openShopInventory(player, shopOpt.get());
        } catch (SQLException e) {
            source.getSender().getServer().getLogger().log(Level.SEVERE, "Failed to edit shop", e);
            player.sendMessage(messages.get("error.storage"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int executeReload(CommandSourceStack source) {
        reloadAction.run();
        source.getSender().sendMessage(messages.get("system.reloaded"));
        return Command.SINGLE_SUCCESS;
    }
}

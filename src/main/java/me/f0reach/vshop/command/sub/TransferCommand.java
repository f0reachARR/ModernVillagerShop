package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.model.Shop;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@SuppressWarnings("UnstableApiUsage")
public final class TransferCommand {

    private final CommandSupport support;

    public TransferCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("transfer")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.coowner.transfer")
                        || s.getSender().hasPermission("modernvillagershop.coowner.transfer.others"))
                .then(Commands.argument("shopId", StringArgumentType.word())
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> execute(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "shopId"),
                                        StringArgumentType.getString(ctx, "player")))));
    }

    private int execute(CommandSender sender, String shopIdPrefix, String targetName) {
        Shop shop = support.findShopByPrefix(shopIdPrefix);
        if (shop == null) {
            support.sendShopNotFound(sender, shopIdPrefix);
            return 0;
        }
        if (!(sender instanceof Player viewer)) {
            support.sendPlayerOnly(sender);
            return 0;
        }
        if (shop.isAdminShop()) {
            viewer.sendMessage(support.messages().get("coowner.admin-shop"));
            return 0;
        }
        support.plugin().coOwnerFlow().openTransfer(viewer, shop, targetName);
        return Command.SINGLE_SUCCESS;
    }
}

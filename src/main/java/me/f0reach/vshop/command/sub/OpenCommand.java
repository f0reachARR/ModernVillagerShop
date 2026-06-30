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
public final class OpenCommand {

    private final CommandSupport support;

    public OpenCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        // ShopOpenService re-checks the open.any / open.<shopId> / open.nearby
        // priority at execution time; here we hide the command from senders who
        // have neither a global nor a nearby open permission.
        return Commands.literal("open")
                .requires(s -> {
                    var sender = s.getSender();
                    return sender.hasPermission("modernvillagershop.use")
                            && (sender.hasPermission("modernvillagershop.open.any")
                                    || sender.hasPermission("modernvillagershop.open.nearby"));
                })
                .then(Commands.argument("shopId", StringArgumentType.word())
                        .executes(ctx -> execute(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "shopId"))));
    }

    private int execute(CommandSender sender, String shopIdPrefix) {
        Shop shop = support.findShopByPrefix(shopIdPrefix);
        if (shop == null) {
            support.sendShopNotFound(sender, shopIdPrefix);
            return 0;
        }
        if (!(sender instanceof Player viewer)) {
            support.sendPlayerOnly(sender);
            return 0;
        }
        support.plugin().openService().open(viewer, shop);
        return Command.SINGLE_SUCCESS;
    }
}

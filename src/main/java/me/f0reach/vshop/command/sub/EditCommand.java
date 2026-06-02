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

import java.sql.SQLException;

@SuppressWarnings("UnstableApiUsage")
public final class EditCommand {

    private final CommandSupport support;

    public EditCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("edit")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.edit")
                        || s.getSender().hasPermission("modernvillagershop.edit.others")
                        || s.getSender().hasPermission("modernvillagershop.admin.edit"))
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
        if (!(sender instanceof Player editor)) {
            support.sendPlayerOnly(sender);
            return 0;
        }
        try {
            if (!support.plugin().editService().canEdit(editor, shop)) {
                editor.sendMessage(support.messages().get("shop.edit.no-permission"));
                return 0;
            }
        } catch (SQLException ex) {
            support.sendGenericError(editor, ex);
            return 0;
        }
        support.plugin().actionMenu().open(editor, shop);
        return Command.SINGLE_SUCCESS;
    }
}

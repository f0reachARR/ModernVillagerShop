package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.model.Shop;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;

@SuppressWarnings("UnstableApiUsage")
public final class StatsCommand {

    private final CommandSupport support;

    public StatsCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("stats")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.stats"))
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
        try {
            var agg = support.plugin().api().statsFor(shop.id());
            int slotCount = support.plugin().storage().slots().findByShop(shop.id()).size();
            EconomyService econ = support.plugin().economyService();
            var mm = support.messages().miniMessage();
            sender.sendMessage(mm.deserialize("<gold>=== " + shop.name() + " 統計 ==="));
            sender.sendMessage(mm.deserialize("<gray>出品枠: <white>" + slotCount));
            sender.sendMessage(mm.deserialize("<gray>SELL件数: <white>" + agg.sellCount()
                    + " <gray>合計: <white>" + econ.format(agg.totalSalesValue())));
            sender.sendMessage(mm.deserialize("<gray>BUY件数: <white>" + agg.buyCount()
                    + " <gray>合計: <white>" + econ.format(agg.totalBuyValue())));
            sender.sendMessage(mm.deserialize("<gray>累計手数料: <white>" + econ.format(agg.totalFees())));
        } catch (SQLException ex) {
            support.sendGenericError(sender, ex);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}

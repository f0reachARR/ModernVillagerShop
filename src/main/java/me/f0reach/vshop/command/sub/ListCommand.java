package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.model.Shop;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class ListCommand {

    private static final int PER_PAGE = 8;

    private final CommandSupport support;

    public ListCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("list")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.list"))
                .executes(ctx -> execute(ctx.getSource().getSender(), 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> execute(ctx.getSource().getSender(),
                                IntegerArgumentType.getInteger(ctx, "page"))));
    }

    private int execute(CommandSender sender, int page) {
        List<Shop> all = new ArrayList<>(support.plugin().registry().all());
        all.sort(Comparator.comparing(Shop::name));
        int total = all.size();
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        int p = Math.min(page, pages);
        int from = (p - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, total);

        var mm = support.messages().miniMessage();
        sender.sendMessage(mm.deserialize("<gold>=== ショップ一覧 (" + p + "/" + pages + ") ==="));
        for (int i = from; i < to; i++) {
            Shop s = all.get(i);
            sender.sendMessage(mm.deserialize(
                    "<yellow>" + s.id().toString().substring(0, 8)
                            + " <gray>- <white>" + s.name()
                            + " <dark_gray>[" + s.type() + (s.suspended() ? " SUSPENDED" : "") + "]"));
        }
        return Command.SINGLE_SUCCESS;
    }
}

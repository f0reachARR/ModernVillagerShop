package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.text.Displays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

        MessageManager messages = support.messages();
        sender.sendMessage(messages.get("command.list.header",
                Placeholder.parsed("page", String.valueOf(p)),
                Placeholder.parsed("pages", String.valueOf(pages)),
                Placeholder.parsed("total", String.valueOf(total))));
        if (total == 0) {
            sender.sendMessage(messages.get("command.list.empty"));
            return Command.SINGLE_SUCCESS;
        }
        for (int i = from; i < to; i++) {
            Shop s = all.get(i);
            // Shop names are player-supplied: insert as a component so a name
            // containing MiniMessage syntax cannot inject formatting.
            sender.sendMessage(messages.get("command.list.line",
                    Placeholder.component("shop_id", Displays.shortId(s.id())),
                    Placeholder.component("shop_name",
                            Displays.nameWithHover(Displays.truncate(s.name(), 24), s.name())),
                    Placeholder.component("type", support.enumLabels().label(s.type())),
                    Placeholder.component("suspended", s.suspended()
                            ? messages.get("command.list.suspended-mark")
                            : Component.empty())));
        }
        return Command.SINGLE_SUCCESS;
    }
}

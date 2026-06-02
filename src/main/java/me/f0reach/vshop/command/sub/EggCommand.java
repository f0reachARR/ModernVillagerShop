package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.shop.egg.SpawnEggFactory;
import me.f0reach.vshop.shop.egg.SpawnEggMeta;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class EggCommand {

    private final CommandSupport support;

    public EggCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("egg")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.egg")
                        || s.getSender().hasPermission("modernvillagershop.admin.egg"))
                .then(Commands.argument("target", ArgumentTypes.player())
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String s : List.of("1", "2", "3", "4", "5", "inf", "admin")) {
                                        b.suggest(s);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(this::execute)));
    }

    private int execute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        var resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        List<Player> targets = resolver.resolve(source);
        if (targets.isEmpty()) {
            source.getSender().sendMessage(support.messages().get("command.egg.not-found",
                    Placeholder.parsed("player", "?")));
            return 0;
        }
        String type = StringArgumentType.getString(ctx, "type").toLowerCase();
        SpawnEggFactory factory = support.plugin().eggFactory();

        SpawnEggMeta meta;
        if ("admin".equals(type)) {
            if (!source.getSender().hasPermission("modernvillagershop.admin.egg")) {
                source.getSender().sendMessage(support.messages().get("command.no-permission"));
                return 0;
            }
            meta = SpawnEggMeta.ofAdmin();
        } else if ("inf".equals(type)) {
            meta = SpawnEggMeta.ofInfinitePlayer();
        } else {
            try {
                int rows = Integer.parseInt(type);
                meta = SpawnEggMeta.ofFixedRows(rows);
            } catch (NumberFormatException nfe) {
                source.getSender().sendMessage(support.messages().get("error.invalid-amount"));
                return 0;
            }
        }

        var stack = factory.create(meta);
        for (Player target : targets) {
            target.getInventory().addItem(stack.clone());
            source.getSender().sendMessage(support.messages().get("command.egg.issued",
                    Placeholder.parsed("player", target.getName())));
        }
        return Command.SINGLE_SUCCESS;
    }
}

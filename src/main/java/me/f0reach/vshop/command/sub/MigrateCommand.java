package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.storage.migrate.MigrationService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

@SuppressWarnings("UnstableApiUsage")
public final class MigrateCommand {

    private final CommandSupport support;

    public MigrateCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("migrate")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.migrate"))
                .then(Commands.argument("from", StringArgumentType.word())
                        .then(Commands.argument("to", StringArgumentType.word())
                                .executes(ctx -> execute(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "from"),
                                        StringArgumentType.getString(ctx, "to")))));
    }

    private int execute(CommandSender sender, String fromType, String toType) {
        sender.sendMessage(support.messages().get("command.migrate.started",
                Placeholder.parsed("from", fromType),
                Placeholder.parsed("to", toType)));
        try {
            int copied = MigrationService.run(support.plugin(), fromType, toType);
            sender.sendMessage(support.messages().get("command.migrate.done")
                    .append(Component.text(" (" + copied + " shops)")));
        } catch (Exception ex) {
            sender.sendMessage(support.messages().get("command.migrate.failed",
                    Placeholder.parsed("reason", String.valueOf(ex.getMessage()))));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }
}

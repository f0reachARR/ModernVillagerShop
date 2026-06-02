package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;

@SuppressWarnings("UnstableApiUsage")
public final class ReloadCommand {

    private final CommandSupport support;

    public ReloadCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("reload")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.reload"))
                .executes(this::execute);
    }

    private int execute(CommandContext<CommandSourceStack> ctx) {
        support.plugin().reloadConfigInternal();
        ctx.getSource().getSender().sendMessage(support.messages().get("command.reload.done"));
        return Command.SINGLE_SUCCESS;
    }
}

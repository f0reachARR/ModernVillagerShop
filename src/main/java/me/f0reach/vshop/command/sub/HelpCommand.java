package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import org.bukkit.command.CommandSender;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class HelpCommand {

    /** Display order; the text of each line lives in {@code command.help.<key>}. */
    private static final List<String> ENTRIES = List.of(
            "list", "open", "edit", "appearance", "coowner", "transfer", "stats",
            "search", "history", "egg", "migrate", "reload");

    private final CommandSupport support;

    public HelpCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("help").executes(this::execute);
    }

    public int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(support.messages().get("command.help.header"));
        for (String entry : ENTRIES) {
            sender.sendMessage(support.messages().get("command.help." + entry));
        }
        return Command.SINGLE_SUCCESS;
    }
}

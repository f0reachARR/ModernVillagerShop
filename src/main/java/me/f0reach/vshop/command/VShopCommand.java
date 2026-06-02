package me.f0reach.vshop.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.command.sub.CoOwnerCommand;
import me.f0reach.vshop.command.sub.EditCommand;
import me.f0reach.vshop.command.sub.EggCommand;
import me.f0reach.vshop.command.sub.HelpCommand;
import me.f0reach.vshop.command.sub.HistoryCommand;
import me.f0reach.vshop.command.sub.ListCommand;
import me.f0reach.vshop.command.sub.MigrateCommand;
import me.f0reach.vshop.command.sub.OpenCommand;
import me.f0reach.vshop.command.sub.ReloadCommand;
import me.f0reach.vshop.command.sub.SearchCommand;
import me.f0reach.vshop.command.sub.StatsCommand;
import me.f0reach.vshop.command.sub.TransferCommand;

/**
 * Brigadier command tree for /vshop. Registered via LifecycleEvents.COMMANDS.
 *
 * Each subcommand lives in its own class under {@link me.f0reach.vshop.command.sub}
 * and exposes a {@code node()} method returning its Brigadier subtree.
 */
@SuppressWarnings("UnstableApiUsage")
public final class VShopCommand {

    private final HelpCommand help;
    private final ReloadCommand reload;
    private final ListCommand list;
    private final OpenCommand open;
    private final EditCommand edit;
    private final CoOwnerCommand coowner;
    private final TransferCommand transfer;
    private final StatsCommand stats;
    private final SearchCommand search;
    private final HistoryCommand history;
    private final MigrateCommand migrate;
    private final EggCommand egg;

    public VShopCommand(ModernVillagerShopPlugin plugin) {
        CommandSupport support = new CommandSupport(plugin);
        this.help = new HelpCommand(support);
        this.reload = new ReloadCommand(support);
        this.list = new ListCommand(support);
        this.open = new OpenCommand(support);
        this.edit = new EditCommand(support);
        this.coowner = new CoOwnerCommand(support);
        this.transfer = new TransferCommand(support);
        this.stats = new StatsCommand(support);
        this.search = new SearchCommand(support);
        this.history = new HistoryCommand(support);
        this.migrate = new MigrateCommand(support);
        this.egg = new EggCommand(support);
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vshop")
                .executes(help::execute)
                .then(help.node())
                .then(reload.node())
                .then(list.node())
                .then(open.node())
                .then(edit.node())
                .then(coowner.node())
                .then(transfer.node())
                .then(stats.node())
                .then(search.node())
                .then(history.node())
                .then(migrate.node())
                .then(egg.node())
                .build();
    }
}

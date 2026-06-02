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

    private static final List<String> LINES = List.of(
            "<yellow>/vshop list [page] <gray>- ショップ一覧",
            "<yellow>/vshop open <shopId> <gray>- ショップを開く",
            "<yellow>/vshop edit <shopId> <gray>- 編集UIを開く",
            "<yellow>/vshop coowner <shopId> <gray>- 共同オーナー管理",
            "<yellow>/vshop transfer <shopId> <player> <gray>- PRIMARY 移譲",
            "<yellow>/vshop stats <shopId> <gray>- ショップ統計",
            "<yellow>/vshop search <item> [page] <gray>- アイテム検索",
            "<yellow>/vshop history [page] [--shop <id>] [--side sell|buy] [--from <date>] [--to <date>] [--player <name>] <gray>- 取引履歴",
            "<yellow>/vshop egg <player> <lines|inf|admin> <gray>- スポーンエッグ配布",
            "<yellow>/vshop migrate <from> <to> <gray>- ストレージ移行",
            "<yellow>/vshop reload <gray>- 設定リロード"
    );

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
        for (String line : LINES) {
            sender.sendMessage(support.messages().miniMessage().deserialize(line));
        }
        return Command.SINGLE_SUCCESS;
    }
}

package me.f0reach.vshop.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.egg.SpawnEggFactory;
import me.f0reach.vshop.shop.egg.SpawnEggMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Brigadier command tree for /vshop. Registered via LifecycleEvents.COMMANDS.
 */
@SuppressWarnings("UnstableApiUsage")
public final class VShopCommand {

    private final ModernVillagerShopPlugin plugin;
    private final MessageManager messages;

    public VShopCommand(ModernVillagerShopPlugin plugin) {
        this.plugin = plugin;
        this.messages = plugin.messages();
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vshop")
                .executes(this::help)
                .then(Commands.literal("help").executes(this::help))
                .then(Commands.literal("reload")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.reload"))
                        .executes(this::reload))
                .then(Commands.literal("list")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.list"))
                        .executes(ctx -> list(ctx.getSource().getSender(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> list(ctx.getSource().getSender(),
                                        IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("open")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.use"))
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .executes(ctx -> open(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "shopId")))))
                .then(Commands.literal("egg")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.egg")
                                || s.getSender().hasPermission("modernvillagershop.admin.egg"))
                        .then(Commands.argument("target", ArgumentTypes.player())
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            b.suggest("1");
                                            b.suggest("2");
                                            b.suggest("3");
                                            b.suggest("4");
                                            b.suggest("5");
                                            b.suggest("inf");
                                            b.suggest("admin");
                                            return b.buildFuture();
                                        })
                                        .executes(this::egg))))
                .build();
    }

    // ---- handlers ----

    private int help(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        sender.sendMessage(messages.get("command.help.header"));
        for (String line : List.of(
                "<yellow>/vshop list [page] <gray>- ショップ一覧",
                "<yellow>/vshop open <shopId> <gray>- ショップを開く",
                "<yellow>/vshop egg <player> <lines|inf|admin> <gray>- スポーンエッグ配布",
                "<yellow>/vshop reload <gray>- 設定リロード"
        )) {
            sender.sendMessage(messages.miniMessage().deserialize(line));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        plugin.reloadConfigInternal();
        ctx.getSource().getSender().sendMessage(messages.get("command.reload.done"));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandSender sender, int page) {
        List<Shop> all = new java.util.ArrayList<>(plugin.registry().all());
        all.sort(java.util.Comparator.comparing(Shop::name));
        int perPage = 8;
        int total = all.size();
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        int p = Math.min(page, pages);
        int from = (p - 1) * perPage;
        int to = Math.min(from + perPage, total);

        sender.sendMessage(messages.miniMessage().deserialize(
                "<gold>=== ショップ一覧 (" + p + "/" + pages + ") ==="));
        for (int i = from; i < to; i++) {
            Shop s = all.get(i);
            sender.sendMessage(messages.miniMessage().deserialize(
                    "<yellow>" + s.id().toString().substring(0, 8)
                            + " <gray>- <white>" + s.name()
                            + " <dark_gray>[" + s.type() + (s.suspended() ? " SUSPENDED" : "") + "]"));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int open(CommandSender sender, String shopIdPrefix) {
        Shop match = findShopByPrefix(shopIdPrefix);
        if (match == null) {
            sender.sendMessage(messages.get("command.shop-not-found",
                    Placeholder.parsed("shop_id", shopIdPrefix)));
            return 0;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("command.player-only"));
            return 0;
        }
        // UI not yet implemented; tell the user how it'll work for now.
        sender.sendMessage(Component.text("[stub] open shop " + match.id()));
        return Command.SINGLE_SUCCESS;
    }

    private Shop findShopByPrefix(String prefix) {
        for (Shop s : plugin.registry().all()) {
            if (s.id().toString().startsWith(prefix)) return s;
        }
        try {
            return plugin.registry().byId(UUID.fromString(prefix)).orElse(null);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int egg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        var resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        List<Player> targets = resolver.resolve(source);
        if (targets.isEmpty()) {
            source.getSender().sendMessage(messages.get("command.egg.not-found",
                    Placeholder.parsed("player", "?")));
            return 0;
        }
        String type = StringArgumentType.getString(ctx, "type").toLowerCase();
        SpawnEggFactory factory = plugin.eggFactory();

        SpawnEggMeta meta;
        if ("admin".equals(type)) {
            if (!source.getSender().hasPermission("modernvillagershop.admin.egg")) {
                source.getSender().sendMessage(messages.get("command.no-permission"));
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
                source.getSender().sendMessage(messages.get("error.invalid-amount"));
                return 0;
            }
        }

        var stack = factory.create(meta);
        for (Player target : targets) {
            target.getInventory().addItem(stack.clone());
            source.getSender().sendMessage(messages.get("command.egg.issued",
                    Placeholder.parsed("player", target.getName())));
        }
        return Command.SINGLE_SUCCESS;
    }
}

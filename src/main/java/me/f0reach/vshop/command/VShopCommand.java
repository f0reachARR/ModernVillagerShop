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
                .then(Commands.literal("edit")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.edit")
                                || s.getSender().hasPermission("modernvillagershop.edit.others")
                                || s.getSender().hasPermission("modernvillagershop.admin.edit"))
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .executes(ctx -> edit(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "shopId")))))
                .then(Commands.literal("coowner")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.coowner.manage")
                                || s.getSender().hasPermission("modernvillagershop.coowner.manage.others"))
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .executes(ctx -> coowner(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "shopId")))))
                .then(Commands.literal("transfer")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.coowner.transfer")
                                || s.getSender().hasPermission("modernvillagershop.coowner.transfer.others"))
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> transfer(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "shopId"),
                                                StringArgumentType.getString(ctx, "player"))))))
                .then(Commands.literal("stats")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.stats"))
                        .then(Commands.argument("shopId", StringArgumentType.word())
                                .executes(ctx -> stats(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "shopId")))))
                .then(Commands.literal("search")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.search"))
                        .then(Commands.argument("item", StringArgumentType.word())
                                .executes(ctx -> search(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "item")))))
                .then(Commands.literal("history")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.history")
                                || s.getSender().hasPermission("modernvillagershop.history.others"))
                        .executes(ctx -> history(ctx.getSource().getSender(), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> history(ctx.getSource().getSender(),
                                        IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("migrate")
                        .requires(s -> s.getSender().hasPermission("modernvillagershop.migrate"))
                        .then(Commands.argument("from", StringArgumentType.word())
                                .then(Commands.argument("to", StringArgumentType.word())
                                        .executes(ctx -> migrate(ctx.getSource().getSender(),
                                                StringArgumentType.getString(ctx, "from"),
                                                StringArgumentType.getString(ctx, "to"))))))
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
                "<yellow>/vshop edit <shopId> <gray>- 編集UIを開く",
                "<yellow>/vshop coowner <shopId> <gray>- 共同オーナー管理",
                "<yellow>/vshop transfer <shopId> <player> <gray>- PRIMARY 移譲",
                "<yellow>/vshop stats <shopId> <gray>- ショップ統計",
                "<yellow>/vshop search <item> <gray>- アイテム検索",
                "<yellow>/vshop history [page] <gray>- 自身の取引履歴",
                "<yellow>/vshop egg <player> <lines|inf|admin> <gray>- スポーンエッグ配布",
                "<yellow>/vshop migrate <from> <to> <gray>- ストレージ移行",
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
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.get("command.player-only"));
            return 0;
        }
        plugin.openService().open(viewer, match);
        return Command.SINGLE_SUCCESS;
    }

    private int edit(CommandSender sender, String shopIdPrefix) {
        Shop match = findShopByPrefix(shopIdPrefix);
        if (match == null) {
            sender.sendMessage(messages.get("command.shop-not-found",
                    Placeholder.parsed("shop_id", shopIdPrefix)));
            return 0;
        }
        if (!(sender instanceof Player editor)) {
            sender.sendMessage(messages.get("command.player-only"));
            return 0;
        }
        try {
            if (!plugin.editService().canEdit(editor, match)) {
                editor.sendMessage(messages.get("shop.edit.no-permission"));
                return 0;
            }
        } catch (java.sql.SQLException ex) {
            editor.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            return 0;
        }
        plugin.editService().beginEditing(match.id());
        plugin.editUi().open(editor, match, 0);
        editor.sendMessage(messages.get("shop.edit.editing",
                Placeholder.parsed("shop_name", match.name())));
        return Command.SINGLE_SUCCESS;
    }

    private int coowner(CommandSender sender, String shopIdPrefix) {
        Shop match = findShopByPrefix(shopIdPrefix);
        if (match == null) {
            sender.sendMessage(messages.get("command.shop-not-found",
                    Placeholder.parsed("shop_id", shopIdPrefix)));
            return 0;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.get("command.player-only"));
            return 0;
        }
        if (match.isAdminShop()) {
            viewer.sendMessage(messages.get("coowner.admin-shop"));
            return 0;
        }
        plugin.coOwnerFlow().openManager(viewer, match);
        return Command.SINGLE_SUCCESS;
    }

    private int transfer(CommandSender sender, String shopIdPrefix, String targetName) {
        Shop match = findShopByPrefix(shopIdPrefix);
        if (match == null) {
            sender.sendMessage(messages.get("command.shop-not-found",
                    Placeholder.parsed("shop_id", shopIdPrefix)));
            return 0;
        }
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.get("command.player-only"));
            return 0;
        }
        if (match.isAdminShop()) {
            viewer.sendMessage(messages.get("coowner.admin-shop"));
            return 0;
        }
        plugin.coOwnerFlow().openTransfer(viewer, match, targetName);
        return Command.SINGLE_SUCCESS;
    }

    private int stats(CommandSender sender, String shopIdPrefix) {
        Shop match = findShopByPrefix(shopIdPrefix);
        if (match == null) {
            sender.sendMessage(messages.get("command.shop-not-found",
                    Placeholder.parsed("shop_id", shopIdPrefix)));
            return 0;
        }
        try {
            var agg = plugin.api().statsFor(match.id());
            int slotCount = plugin.storage().slots().findByShop(match.id()).size();
            var econ = plugin.economyService();
            var mm = messages.miniMessage();
            sender.sendMessage(mm.deserialize("<gold>=== " + match.name() + " 統計 ==="));
            sender.sendMessage(mm.deserialize("<gray>出品枠: <white>" + slotCount));
            sender.sendMessage(mm.deserialize("<gray>SELL件数: <white>" + agg.sellCount()
                    + " <gray>合計: <white>" + econ.format(agg.totalSalesValue())));
            sender.sendMessage(mm.deserialize("<gray>BUY件数: <white>" + agg.buyCount()
                    + " <gray>合計: <white>" + econ.format(agg.totalBuyValue())));
            sender.sendMessage(mm.deserialize("<gray>累計手数料: <white>" + econ.format(agg.totalFees())));
        } catch (java.sql.SQLException ex) {
            sender.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int search(CommandSender sender, String query) {
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(query);
        var mm = messages.miniMessage();
        sender.sendMessage(mm.deserialize("<gold>=== 検索: " + query + " ==="));
        int hits = 0;
        try {
            for (Shop s : plugin.registry().all()) {
                for (var slot : plugin.storage().slots().findByShop(s.id())) {
                    boolean match = false;
                    if (material != null) {
                        match = slot.itemTemplate().getType() == material;
                    }
                    if (!match) {
                        match = slot.itemTemplate().getType().name().toLowerCase().contains(needle);
                    }
                    if (!match) continue;
                    sender.sendMessage(mm.deserialize("<yellow>" + s.id().toString().substring(0, 8)
                            + " <gray>" + s.name() + " <dark_gray>[" + slot.side()
                            + " @ " + slot.unitPrice() + "]"));
                    if (++hits >= 30) {
                        sender.sendMessage(mm.deserialize("<gray>(以降は省略)"));
                        return Command.SINGLE_SUCCESS;
                    }
                }
            }
        } catch (java.sql.SQLException ex) {
            sender.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            return 0;
        }
        if (hits == 0) sender.sendMessage(mm.deserialize("<gray>ヒットなし"));
        return Command.SINGLE_SUCCESS;
    }

    private int history(CommandSender sender, int page) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.get("command.player-only"));
            return 0;
        }
        int perPage = 10;
        try {
            var records = plugin.storage().transactions().findByPlayer(viewer.getUniqueId(),
                    perPage, (page - 1) * perPage);
            var mm = messages.miniMessage();
            var econ = plugin.economyService();
            sender.sendMessage(mm.deserialize("<gold>=== 取引履歴 (p." + page + ") ==="));
            if (records.isEmpty()) {
                sender.sendMessage(mm.deserialize("<gray>履歴がありません"));
                return Command.SINGLE_SUCCESS;
            }
            for (var rec : records) {
                String when = java.time.format.DateTimeFormatter
                        .ofPattern("MM-dd HH:mm")
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(rec.at());
                sender.sendMessage(mm.deserialize("<gray>" + when + " <yellow>" + rec.side()
                        + " <white>" + rec.itemSnapshot().getType().name()
                        + " <gray>x" + rec.amount() + " @ <white>" + econ.format(rec.unitPrice())));
            }
        } catch (java.sql.SQLException ex) {
            sender.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int migrate(CommandSender sender, String fromType, String toType) {
        sender.sendMessage(messages.get("command.migrate.started",
                Placeholder.parsed("from", fromType),
                Placeholder.parsed("to", toType)));
        try {
            int copied = me.f0reach.vshop.storage.migrate.MigrationService.run(plugin, fromType, toType);
            sender.sendMessage(messages.get("command.migrate.done")
                    .append(Component.text(" (" + copied + " shops)")));
        } catch (Exception ex) {
            sender.sendMessage(messages.get("command.migrate.failed",
                    Placeholder.parsed("reason", ex.getMessage())));
            return 0;
        }
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

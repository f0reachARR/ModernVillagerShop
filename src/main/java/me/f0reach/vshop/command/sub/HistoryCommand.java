package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.shop.cache.PlayerCacheService;
import me.f0reach.vshop.storage.repo.ShopTransactionRepository.HistoryFilter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public final class HistoryCommand {

    private static final int PER_PAGE = 10;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter
            .ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CommandSupport support;

    public HistoryCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("history")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.history")
                        || s.getSender().hasPermission("modernvillagershop.history.others"))
                .executes(ctx -> execute(ctx.getSource().getSender(), ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(this::suggestFlags)
                        .executes(this::executeWithArgs));
    }

    private int executeWithArgs(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "args");
        return execute(ctx.getSource().getSender(), raw);
    }

    private int execute(CommandSender sender, String raw) {
        HistoryArgs.Result parsed = HistoryArgs.parse(raw);
        if (parsed.isError()) {
            sender.sendMessage(support.messages().get("command.history.bad-flag",
                    Placeholder.parsed("reason", parsed.error())));
            return 0;
        }
        HistoryArgs args = parsed.value();

        // Resolve --shop / shopId prefix to a Shop instance, if specified.
        UUID resolvedShopId = null;
        if (args.shopIdPrefix() != null) {
            Shop shop = support.findShopByPrefix(args.shopIdPrefix());
            if (shop == null) {
                support.sendShopNotFound(sender, args.shopIdPrefix());
                return 0;
            }
            resolvedShopId = shop.id();
        }

        // Resolve --player name to UUID via the player cache when supplied. If
        // no shop and no player flag, default to self (current behaviour).
        UUID resolvedPlayerUuid = null;
        boolean filteringForOthers = false;
        if (args.playerName() != null) {
            PlayerCacheService cache = support.plugin().playerCacheService();
            var entry = cache.findByName(args.playerName()).orElse(null);
            if (entry == null) {
                // Fall back to OfflinePlayer name lookup (may be a never-cached
                // player). isHasPlayedBefore is the strongest hint we have.
                var op = org.bukkit.Bukkit.getOfflinePlayerIfCached(args.playerName());
                if (op == null) {
                    sender.sendMessage(support.messages().get("command.history.player-not-found",
                            Placeholder.parsed("player", args.playerName())));
                    return 0;
                }
                resolvedPlayerUuid = op.getUniqueId();
            } else {
                resolvedPlayerUuid = entry.playerUuid();
            }
            filteringForOthers = true;
        } else if (resolvedShopId == null) {
            if (!(sender instanceof Player viewer)) {
                support.sendPlayerOnly(sender);
                return 0;
            }
            resolvedPlayerUuid = viewer.getUniqueId();
        }

        // Permission gate: looking at someone else's history (either an
        // explicit --player different from self, or a --shop on which the
        // sender isn't a co-owner) needs history.others.
        if (!hasOthersAccess(sender, resolvedShopId, resolvedPlayerUuid, filteringForOthers)) {
            sender.sendMessage(support.messages().get("command.no-permission"));
            return 0;
        }

        HistoryFilter filter = new HistoryFilter(
                resolvedShopId, resolvedPlayerUuid, args.side(), args.from(), args.to());
        int page = args.pageOrDefault();
        try {
            List<TradeRecord> records = support.plugin().storage().transactions()
                    .findFiltered(filter, PER_PAGE, (page - 1) * PER_PAGE);
            long total = support.plugin().storage().transactions().countFiltered(filter);
            renderHeader(sender, page, total, filter);
            if (records.isEmpty()) {
                sender.sendMessage(support.messages().get("command.history.empty"));
                return Command.SINGLE_SUCCESS;
            }
            EconomyService econ = support.plugin().economyService();
            MiniMessage mm = support.messages().miniMessage();
            for (TradeRecord rec : records) {
                String when = TIME_FMT.format(rec.at());
                String counterparty = resolveCounterparty(rec);
                String shopName = shopName(rec.shopId());
                StringBuilder line = new StringBuilder("<gray>")
                        .append(when)
                        .append(" <yellow>").append(rec.side())
                        .append(" <white>").append(rec.itemSnapshot().getType().name())
                        .append(" <gray>x").append(rec.amount())
                        .append(" @ <white>").append(econ.format(rec.unitPrice()));
                if (rec.fee() != null && rec.fee().signum() > 0) {
                    line.append(" <gray>(fee ").append(econ.format(rec.fee())).append(")");
                }
                line.append(" <dark_gray>[").append(shopName).append(" / ").append(counterparty).append("]");
                sender.sendMessage(mm.deserialize(line.toString()));
            }
            int pages = (int) Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
            if (page < pages) {
                sender.sendMessage(support.messages().get("command.history.next-hint",
                        Placeholder.parsed("args", nextPageArgs(raw, page + 1))));
            }
        } catch (SQLException ex) {
            support.sendGenericError(sender, ex);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private boolean hasOthersAccess(CommandSender sender, UUID shopId, UUID playerUuid, boolean explicitPlayer) {
        if (sender.hasPermission("modernvillagershop.history.others")) return true;
        if (!(sender instanceof Player viewer)) return true; // console
        UUID self = viewer.getUniqueId();
        if (explicitPlayer && (playerUuid == null || !playerUuid.equals(self))) return false;
        if (shopId != null) {
            // Allow if viewer is a co-owner of that shop (any role).
            try {
                var roles = support.plugin().storage().coOwners().findByShop(shopId);
                for (var co : roles) {
                    if (co.playerUuid().equals(self)) return true;
                }
                return false;
            } catch (SQLException ex) {
                return false;
            }
        }
        return true;
    }

    private String resolveCounterparty(TradeRecord rec) {
        UUID other = rec.side() == me.f0reach.vshop.model.TradeSide.SELL
                ? rec.buyerUuid() : rec.sellerUuid();
        if (other == null) return "-";
        var entry = support.plugin().playerCacheService().findByUuid(other).orElse(null);
        if (entry != null) return entry.name();
        var op = org.bukkit.Bukkit.getOfflinePlayer(other);
        return op.getName() != null ? op.getName() : other.toString().substring(0, 8);
    }

    private String shopName(UUID shopId) {
        return support.plugin().registry().byId(shopId)
                .map(Shop::name)
                .orElseGet(() -> shopId.toString().substring(0, 8));
    }

    private void renderHeader(CommandSender sender, int page, long total, HistoryFilter f) {
        StringBuilder filters = new StringBuilder();
        if (f.shopId() != null) filters.append(" shop=").append(f.shopId().toString(), 0, 8);
        if (f.side() != null) filters.append(" side=").append(f.side());
        if (f.from() != null) filters.append(" from=").append(TIME_FMT.format(f.from()));
        if (f.to() != null) filters.append(" to=").append(TIME_FMT.format(f.to()));
        if (f.playerUuid() != null) filters.append(" player=")
                .append(support.plugin().playerCacheService().findByUuid(f.playerUuid())
                        .map(e -> e.name()).orElse(f.playerUuid().toString().substring(0, 8)));
        int pages = (int) Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        sender.sendMessage(support.messages().get("command.history.header",
                Placeholder.parsed("page", String.valueOf(page)),
                Placeholder.parsed("pages", String.valueOf(pages)),
                Placeholder.parsed("total", String.valueOf(total)),
                Placeholder.parsed("filters", filters.toString())));
    }

    /** Replace or append --page in the existing arg string so the suggested next-page link round-trips. */
    private static String nextPageArgs(String raw, int nextPage) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) return String.valueOf(nextPage);
        String[] tokens = trimmed.split("\\s+");
        StringBuilder out = new StringBuilder();
        boolean replacedPositional = false;
        boolean replacedFlag = false;
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            if (!replacedFlag && "--page".equals(tok) && i + 1 < tokens.length) {
                out.append(tok).append(' ').append(nextPage).append(' ');
                i++;
                replacedFlag = true;
                continue;
            }
            if (!replacedPositional && !replacedFlag && !tok.startsWith("--") && isInteger(tok)) {
                out.append(nextPage).append(' ');
                replacedPositional = true;
                continue;
            }
            out.append(tok).append(' ');
        }
        if (!replacedPositional && !replacedFlag) out.append("--page ").append(nextPage);
        return out.toString().trim();
    }

    private static boolean isInteger(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFlags(
            CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String head = lastSpace < 0 ? "" : remaining.substring(0, lastSpace + 1);
        String tail = lastSpace < 0 ? remaining : remaining.substring(lastSpace + 1);
        for (String flag : List.of("--shop", "--side", "--from", "--to", "--player", "--page")) {
            if (flag.startsWith(tail)) builder.suggest(head + flag);
        }
        if ("--side".equals(prevToken(remaining))) {
            builder.suggest(head + "SELL");
            builder.suggest(head + "BUY");
        }
        return builder.buildFuture();
    }

    private static String prevToken(String raw) {
        String trimmed = raw.trim();
        int last = trimmed.lastIndexOf(' ');
        if (last < 0) return "";
        String before = trimmed.substring(0, last).trim();
        int prev = before.lastIndexOf(' ');
        return prev < 0 ? before : before.substring(prev + 1);
    }
}

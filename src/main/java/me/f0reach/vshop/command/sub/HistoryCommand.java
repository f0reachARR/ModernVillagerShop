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
import me.f0reach.vshop.ui.text.Displays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            for (TradeRecord rec : records) {
                String when = TIME_FMT.format(rec.at());
                String counterparty = resolveCounterparty(rec);
                Component shopLabel = shopLabel(rec.shopId());
                Component line = Component.text(when + " ", NamedTextColor.GRAY)
                        .append(Component.text(rec.side().name() + " ", NamedTextColor.YELLOW))
                        .append(Displays.item(rec.itemSnapshot()).color(NamedTextColor.WHITE))
                        .append(Component.text(" x" + rec.amount() + " @ ", NamedTextColor.GRAY))
                        .append(Component.text(econ.format(rec.unitPrice()), NamedTextColor.WHITE));
                if (rec.fee() != null && rec.fee().signum() > 0) {
                    line = line.append(Component.text(" (fee " + econ.format(rec.fee()) + ")",
                            NamedTextColor.GRAY));
                }
                line = line.append(Component.text(" [", NamedTextColor.DARK_GRAY))
                        .append(shopLabel)
                        .append(Component.text(" / " + counterparty + "]", NamedTextColor.DARK_GRAY));
                sender.sendMessage(line);
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

    /**
     * Shop name component with the full shop UUID and ID in a hover, so the
     * truncated text never silently drops information.
     */
    private Component shopLabel(UUID shopId) {
        Shop shop = support.plugin().registry().byId(shopId).orElse(null);
        String visible = shop != null ? Displays.truncate(shop.name(), 24)
                : shopId.toString().substring(0, 8);
        Component hover = Component.text("id=" + shopId, NamedTextColor.GRAY);
        if (shop != null && shop.name().length() > 24) {
            hover = Component.text(shop.name(), NamedTextColor.WHITE)
                    .appendNewline()
                    .append(hover);
        }
        return Component.text(visible)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover));
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

    private static final List<String> ALL_FLAGS = List.of(
            "--shop", "--side", "--from", "--to", "--player", "--page");

    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFlags(
            CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        boolean endsOnSpace = !remaining.isEmpty() && Character.isWhitespace(remaining.charAt(remaining.length() - 1));
        String[] tokens = remaining.isBlank() ? new String[0] : remaining.trim().split("\\s+");

        // Head = everything up to (but not including) the token under the cursor.
        // tail = the token currently being typed (empty if the user just typed a space).
        String head;
        String tail;
        if (endsOnSpace || tokens.length == 0) {
            head = remaining;
            tail = "";
        } else {
            tail = tokens[tokens.length - 1];
            head = remaining.substring(0, remaining.length() - tail.length());
        }

        // Determine which flag (if any) is awaiting a value at the cursor.
        // That's "the previous token is a value-taking flag AND the cursor is
        // sitting on either an empty tail (just after the flag+space) or the
        // start of the value".
        String prev = previousToken(tokens, endsOnSpace);

        if ("--side".equals(prev)) {
            suggestStarting(builder, head, tail, List.of("SELL", "BUY"));
            return builder.buildFuture();
        }
        if ("--shop".equals(prev)) {
            for (Shop s : support.plugin().registry().all()) {
                suggestOne(builder, head, tail, s.id().toString().substring(0, 8));
            }
            return builder.buildFuture();
        }
        if ("--player".equals(prev)) {
            // Pull the most-recently-seen 32 cache entries; filter by prefix.
            var entries = support.plugin().playerCacheService().page(0, 32, false);
            for (var e : entries) suggestOne(builder, head, tail, e.name());
            return builder.buildFuture();
        }
        if ("--from".equals(prev) || "--to".equals(prev)) {
            // Just hint the format — don't pre-fill noisy timestamps.
            if (tail.isEmpty()) {
                builder.suggest(head + "YYYY-MM-DD");
            }
            return builder.buildFuture();
        }
        if ("--page".equals(prev)) {
            if (tail.isEmpty()) builder.suggest(head + "1");
            return builder.buildFuture();
        }

        // Otherwise: we're at the start, or just after a complete flag+value, or
        // mid-typing a new flag. Suggest the flags we haven't used yet.
        java.util.Set<String> used = collectUsedFlags(tokens);
        for (String flag : ALL_FLAGS) {
            if (used.contains(flag)) continue;
            if (flag.startsWith(tail)) builder.suggest(head + flag);
        }
        return builder.buildFuture();
    }

    private static void suggestStarting(com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
                                        String head, String tail, List<String> values) {
        for (String v : values) {
            if (v.toLowerCase(java.util.Locale.ROOT).startsWith(tail.toLowerCase(java.util.Locale.ROOT))) {
                builder.suggest(head + v);
            }
        }
    }

    private static void suggestOne(com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
                                   String head, String tail, String value) {
        if (value == null) return;
        if (tail.isEmpty() || value.toLowerCase(java.util.Locale.ROOT)
                .startsWith(tail.toLowerCase(java.util.Locale.ROOT))) {
            builder.suggest(head + value);
        }
    }

    /**
     * The token immediately before the cursor — used to detect "we're typing a
     * value for this flag". If the cursor is mid-token, the "previous token" is
     * tokens[len-2]; if the cursor is just after a space, it's tokens[len-1].
     */
    private static String previousToken(String[] tokens, boolean endsOnSpace) {
        if (tokens.length == 0) return "";
        if (endsOnSpace) return tokens[tokens.length - 1];
        return tokens.length >= 2 ? tokens[tokens.length - 2] : "";
    }

    private static java.util.Set<String> collectUsedFlags(String[] tokens) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String t : tokens) if (t.startsWith("--")) out.add(t);
        return out;
    }
}

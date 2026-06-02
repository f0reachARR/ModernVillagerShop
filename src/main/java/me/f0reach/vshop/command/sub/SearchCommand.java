package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("UnstableApiUsage")
public final class SearchCommand {

    private static final int PER_PAGE = 10;

    private final CommandSupport support;

    public SearchCommand(CommandSupport support) {
        this.support = support;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("search")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.search"))
                .then(Commands.argument("item", StringArgumentType.word())
                        .executes(ctx -> execute(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "item"), 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> execute(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "item"),
                                        IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private int execute(CommandSender sender, String query, int page) {
        String needle = query.toLowerCase(Locale.ROOT);
        Material material = Material.matchMaterial(query);
        var mm = support.messages().miniMessage();

        List<Hit> hits = new ArrayList<>();
        try {
            for (Shop s : support.plugin().registry().all()) {
                for (ShopSlot slot : support.plugin().storage().slots().findByShop(s.id())) {
                    boolean match = material != null && slot.itemTemplate().getType() == material;
                    if (!match) match = slot.itemTemplate().getType().name()
                            .toLowerCase(Locale.ROOT).contains(needle);
                    if (match) hits.add(new Hit(s, slot));
                }
            }
        } catch (SQLException ex) {
            support.sendGenericError(sender, ex);
            return 0;
        }

        int total = hits.size();
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        int p = Math.min(page, pages);
        int from = (p - 1) * PER_PAGE;
        int to = Math.min(from + PER_PAGE, total);

        sender.sendMessage(support.messages().get("command.search.header",
                Placeholder.parsed("query", query),
                Placeholder.parsed("page", String.valueOf(p)),
                Placeholder.parsed("pages", String.valueOf(pages)),
                Placeholder.parsed("total", String.valueOf(total))));
        if (total == 0) {
            sender.sendMessage(support.messages().get("command.search.empty"));
            return Command.SINGLE_SUCCESS;
        }
        for (int i = from; i < to; i++) {
            Hit h = hits.get(i);
            sender.sendMessage(mm.deserialize("<yellow>" + h.shop.id().toString().substring(0, 8)
                    + " <gray>" + h.shop.name() + " <dark_gray>[" + h.slot.side()
                    + " @ " + h.slot.unitPrice() + "]"));
        }
        if (p < pages) {
            sender.sendMessage(support.messages().get("command.search.next-hint",
                    Placeholder.parsed("query", query),
                    Placeholder.parsed("page", String.valueOf(p + 1))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private record Hit(Shop shop, ShopSlot slot) {}
}

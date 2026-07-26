package me.f0reach.vshop.ui.text;

import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.locale.EnumLabels;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.repo.ShopTransactionRepository.AggregateStats;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/**
 * Chat rendering for a shop's aggregate stats, shared by {@code /vshop stats}
 * and the "Stats" button on the shop action menu so the two can't drift.
 *
 * <p>The per-side lines reuse the {@code stats.side-line} key once per
 * {@link TradeSide}, with the side name coming from the locale rather than
 * being baked into the message.</p>
 */
public final class StatsView {

    private final MessageManager messages;
    private final EnumLabels enumLabels;
    private final EconomyService economy;

    public StatsView(MessageManager messages, EconomyService economy) {
        this.messages = messages;
        this.economy = economy;
        this.enumLabels = new EnumLabels(messages);
    }

    public void send(Audience to, Shop shop, AggregateStats agg, int slotCount) {
        to.sendMessage(messages.get("stats.header",
                Placeholder.component("shop_name",
                        Displays.nameWithHover(Displays.truncate(shop.name(), 32), shop.name()))));
        to.sendMessage(messages.get("stats.slots",
                Placeholder.parsed("count", String.valueOf(slotCount))));
        to.sendMessage(sideLine(TradeSide.SELL, agg.sellCount(), agg.totalSalesValue()));
        to.sendMessage(sideLine(TradeSide.BUY, agg.buyCount(), agg.totalBuyValue()));
        to.sendMessage(messages.get("stats.fees",
                Placeholder.parsed("fees", economy.format(agg.totalFees()))));
    }

    private net.kyori.adventure.text.Component sideLine(TradeSide side, long count,
                                                        java.math.BigDecimal total) {
        return messages.get("stats.side-line",
                Placeholder.component("side", enumLabels.label(side)),
                Placeholder.parsed("count", String.valueOf(count)),
                Placeholder.parsed("total", economy.format(total)));
    }
}

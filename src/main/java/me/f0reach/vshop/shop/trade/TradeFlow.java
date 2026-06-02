package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.ui.dialog.DialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;

/**
 * Drives the SELL/BUY dialog sequence: side picker (only for BOTH slots) →
 * amount input → confirm → execute → notice. All dialog callbacks are
 * dispatched to the main thread by {@link DialogService}, so handlers can
 * freely touch Bukkit state.
 */
public final class TradeFlow {

    private final DialogService dialogs;
    private final TradeService tradeService;
    private final MessageManager messages;
    private final EconomyService economy;
    private final PluginConfig config;

    public TradeFlow(DialogService dialogs, TradeService tradeService, MessageManager messages,
                     EconomyService economy, PluginConfig config) {
        this.dialogs = dialogs;
        this.tradeService = tradeService;
        this.messages = messages;
        this.economy = economy;
        this.config = config;
    }

    public void start(Player viewer, Shop shop, ShopSlot slot) {
        if (shop.suspended()) {
            viewer.sendMessage(messages.get("shop.open.suspended"));
            return;
        }
        if (slot.side() == TradeSide.BOTH) {
            showSidePicker(viewer, shop, slot);
        } else if (slot.allowsSell()) {
            promptAmount(viewer, shop, slot, TradeSide.SELL);
        } else if (slot.allowsBuy()) {
            promptAmount(viewer, shop, slot, TradeSide.BUY);
        } else {
            viewer.sendMessage(messages.get("trade.not-tradable"));
        }
    }

    private void showSidePicker(Player viewer, Shop shop, ShopSlot slot) {
        dialogs.multiButton(viewer,
                messages.get("trade.side-pick.title"),
                messages.get("trade.side-pick.body"),
                List.of(
                        new DialogService.ButtonSpec(messages.get("trade.side-pick.sell"),
                                () -> promptAmount(viewer, shop, slot, TradeSide.SELL)),
                        new DialogService.ButtonSpec(messages.get("trade.side-pick.buy"),
                                () -> promptAmount(viewer, shop, slot, TradeSide.BUY))
                ));
    }

    private void promptAmount(Player viewer, Shop shop, ShopSlot slot, TradeSide side) {
        Component itemName = displayName(slot);
        Component title = messages.get("trade.amount-prompt.title");
        Component body = messages.get("trade.amount-prompt.body",
                Placeholder.component("item", itemName));
        Component submit = messages.get("trade.amount-prompt.submit");
        Component label = messages.get("trade.amount-prompt.label",
                Placeholder.parsed("unit", Integer.toString(slot.unitAmount())));

        dialogs.input(viewer, title, body, submit)
                .text("packs", label, "1")
                .onSubmit(response -> {
                    String raw = response.getText("packs");
                    int packs;
                    try {
                        packs = Integer.parseInt(raw.trim());
                    } catch (NumberFormatException ex) {
                        viewer.sendMessage(messages.get("error.invalid-amount"));
                        return;
                    }
                    if (packs <= 0 || packs * slot.unitAmount() > config.economy().amountMax()) {
                        viewer.sendMessage(messages.get("error.invalid-amount"));
                        return;
                    }
                    showConfirm(viewer, shop, slot, side, packs);
                });
    }

    private void showConfirm(Player viewer, Shop shop, ShopSlot slot, TradeSide side, int packs) {
        BigDecimal unit = side == TradeSide.SELL
                ? slot.unitPrice()
                : (slot.buyUnitPrice() != null ? slot.buyUnitPrice() : slot.unitPrice());
        BigDecimal gross = unit.multiply(BigDecimal.valueOf(packs));
        BigDecimal fee = economy.computeFee(gross);
        BigDecimal payout = gross.subtract(fee);
        int totalItems = packs * slot.unitAmount();

        Component item = displayName(slot);

        String key = side == TradeSide.SELL ? "trade.confirm-sell" : "trade.confirm-buy";
        Component body = messages.get(key + ".body",
                Placeholder.component("item", item),
                Placeholder.parsed("amount", Integer.toString(totalItems)),
                Placeholder.parsed("packs", Integer.toString(packs)),
                Placeholder.parsed("unit_price", economy.format(unit)),
                Placeholder.parsed("price", economy.format(gross)),
                Placeholder.parsed("payout", economy.format(payout)),
                Placeholder.parsed("fee", economy.format(fee)));
        Component title = messages.get(key + ".title");
        Component yes = messages.get(key + ".yes");
        Component no = messages.get(key + ".no");

        dialogs.confirmOnce(viewer, title, body, yes, no,
                () -> execute(viewer, shop, slot, side, packs, unit),
                () -> {});
    }

    private void execute(Player viewer, Shop shop, ShopSlot slot, TradeSide side, int packs, BigDecimal unitSnapshot) {
        TradeRequest req = new TradeRequest(viewer, shop, slot, side, packs, unitSnapshot);
        TradeResult result = tradeService.execute(req);
        if (result instanceof TradeResult.Success ok) {
            String key = ok.side() == TradeSide.SELL ? "trade.sold" : "trade.bought";
            viewer.sendMessage(messages.get(key,
                    Placeholder.component("item", displayName(slot)),
                    Placeholder.parsed("amount", Integer.toString(ok.amount())),
                    Placeholder.parsed("price", economy.format(ok.gross())),
                    Placeholder.parsed("fee", economy.format(ok.fee()))));
        } else if (result instanceof TradeResult.Failure fail) {
            viewer.sendMessage(messages.get(fail.messageKey()));
        }
    }

    private Component displayName(ShopSlot slot) {
        var meta = slot.itemTemplate().getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            var dn = meta.displayName();
            if (dn != null) return dn;
        }
        return Component.text(slot.itemTemplate().getType().name());
    }
}

package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeLimitUsage;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;
import me.f0reach.vshop.ui.dialog.DialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Drives the SELL/BUY dialog sequence: side picker (only for BOTH slots) →
 * amount input → confirm → execute → notice. All dialog callbacks are
 * dispatched to the main thread by {@link DialogService}, so handlers can
 * freely touch Bukkit state.
 */
public final class TradeFlow {

    private static final DateTimeFormatter FROZEN_AT_FMT = DateTimeFormatter
            .ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private static final Logger LOG = Logger.getLogger(TradeFlow.class.getName());

    private final DialogService dialogs;
    private final TradeService tradeService;
    private final MessageManager messages;
    private final EconomyService economy;
    private final PluginConfig config;
    private final PriceResolver priceResolver;
    private final StorageManager storage;

    public TradeFlow(DialogService dialogs, TradeService tradeService, MessageManager messages,
                     EconomyService economy, PluginConfig config, PriceResolver priceResolver,
                     StorageManager storage) {
        this.dialogs = dialogs;
        this.tradeService = tradeService;
        this.messages = messages;
        this.economy = economy;
        this.config = config;
        this.priceResolver = priceResolver;
        this.storage = storage;
    }

    public void start(Player viewer, Shop shop, ShopSlot slot) {
        if (shop.suspended()) {
            viewer.sendMessage(messages.get("shop.open.suspended"));
            return;
        }
        if (slot.side() == TradeSide.BOTH) {
            showSidePicker(viewer, shop, slot);
        } else if (slot.allowsSell()) {
            startSell(viewer, shop, slot);
        } else if (slot.allowsBuy()) {
            startBuy(viewer, shop, slot);
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
                                () -> startSell(viewer, shop, slot)),
                        new DialogService.ButtonSpec(messages.get("trade.side-pick.buy"),
                                () -> startBuy(viewer, shop, slot))
                ));
    }

    /**
     * Pre-check stock and trade-limit so we never walk the player through the
     * amount + confirm dialogs only to fail at execute(). The stock snapshot
     * taken here is also fed into {@link #promptAmount} so the amount input
     * can bound-check without a second DB round-trip.
     */
    private void startSell(Player viewer, Shop shop, ShopSlot slot) {
        int stock = sellStock(shop, slot);
        if (shop.isPlayerShop() && stock < slot.unitAmount()) {
            viewer.sendMessage(messages.get("trade.out-of-stock"));
            return;
        }
        int remaining = remainingTradeLimit(slot, viewer.getUniqueId());
        if (remaining < slot.unitAmount()) {
            viewer.sendMessage(messages.get("trade.limit-reached"));
            return;
        }
        promptAmount(viewer, shop, slot, TradeSide.SELL, stock, remaining, Integer.MAX_VALUE);
    }

    /**
     * Pre-check BUY capacity, trade-limit, and the deliverer's own inventory
     * so we never walk a player who cannot deliver a single pack through the
     * amount dialog. Unlimited (-1) capacity slots always pass capacity;
     * finite slots need at least one full pack of headroom.
     */
    private void startBuy(Player viewer, Shop shop, ShopSlot slot) {
        if (!slot.hasBuyCapacityFor(slot.unitAmount())) {
            viewer.sendMessage(messages.get("trade.buy-full"));
            return;
        }
        int held = playerStock(viewer, slot.itemTemplate());
        if (held < slot.unitAmount()) {
            viewer.sendMessage(messages.get("trade.not-enough-items", Map.of(
                    "required", Integer.toString(slot.unitAmount()),
                    "held", Integer.toString(held))));
            return;
        }
        int remaining = remainingTradeLimit(slot, viewer.getUniqueId());
        if (remaining < slot.unitAmount()) {
            viewer.sendMessage(messages.get("trade.limit-reached"));
            return;
        }
        promptAmount(viewer, shop, slot, TradeSide.BUY, Integer.MAX_VALUE, remaining, held);
    }

    private void promptAmount(Player viewer, Shop shop, ShopSlot slot, TradeSide side,
                              int sellStockSnapshot, int limitRemainingSnapshot,
                              int buyHeldSnapshot) {
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
                    if (packs <= 0) {
                        viewer.sendMessage(messages.get("error.invalid-amount"));
                        return;
                    }
                    int totalItems = packs * slot.unitAmount();
                    int amountMax = config.economy().amountMax();
                    if (totalItems > amountMax) {
                        viewer.sendMessage(messages.get("error.amount-too-large",
                                Placeholder.parsed("max", Integer.toString(amountMax)),
                                Placeholder.parsed("requested", Integer.toString(totalItems))));
                        return;
                    }
                    // Bound the requested amount against current stock / capacity /
                    // trade-limit so we don't show a confirm dialog that's
                    // guaranteed to fail.
                    if (side == TradeSide.SELL && shop.isPlayerShop() && sellStockSnapshot < totalItems) {
                        viewer.sendMessage(messages.get("trade.out-of-stock"));
                        return;
                    }
                    if (side == TradeSide.BUY && !slot.hasBuyCapacityFor(totalItems)) {
                        viewer.sendMessage(messages.get("trade.buy-full"));
                        return;
                    }
                    if (side == TradeSide.BUY && buyHeldSnapshot < totalItems) {
                        viewer.sendMessage(messages.get("trade.not-enough-items", Map.of(
                                "required", Integer.toString(totalItems),
                                "held", Integer.toString(buyHeldSnapshot))));
                        return;
                    }
                    if (limitRemainingSnapshot < totalItems) {
                        viewer.sendMessage(messages.get("trade.limit-reached"));
                        return;
                    }
                    showConfirm(viewer, shop, slot, side, packs);
                });
    }

    /**
     * Returns how many more items the player can still trade through this slot
     * before the configured limit kicks in. {@link Integer#MAX_VALUE} when no
     * limit is set or on read failure (the authoritative check happens inside
     * the trade transaction).
     *
     * <p>The reset-window logic here mirrors
     * {@link TradeService#checkAndReserveLimit} — keep them in sync.</p>
     */
    private int remainingTradeLimit(ShopSlot slot, UUID player) {
        if (slot.tradeLimit() == null) return Integer.MAX_VALUE;
        UUID key = slot.limitScope() == LimitScope.GLOBAL
                ? ShopLimitRepository.GLOBAL_KEY : player;
        try (Connection c = storage.dataSource().getConnection()) {
            Optional<TradeLimitUsage> found = storage.limits().findTx(c, slot.id(), key);
            if (found.isEmpty()) return slot.tradeLimit();
            int current = found.get().amount();
            Instant windowStart = found.get().windowStart();
            if (slot.resetPeriod() != null && windowStart != null) {
                long deadline = windowStart.toEpochMilli() + slot.resetPeriod().toMillis();
                if (Instant.now().toEpochMilli() >= deadline) current = 0;
            }
            return Math.max(0, slot.tradeLimit() - current);
        } catch (SQLException ex) {
            LOG.warning("Trade-limit pre-check failed for slot " + slot.id() + ": " + ex.getMessage());
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Counts how many items the player currently holds that match the slot's
     * template. Used to pre-check BUY (deliverer) availability so we don't walk
     * an empty-handed player through the amount + confirm dialogs.
     */
    private int playerStock(Player player, ItemStack template) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null) continue;
            if (ItemIdentity.sameItem(stack, template)) total += stack.getAmount();
        }
        return total;
    }

    /**
     * Best-effort stock total for SELL pre-check. Admin shops are infinite;
     * player shops sum the inventory rows that match the slot's template. On
     * SQL failure we err on the side of letting the trade through — the
     * authoritative check happens inside the trade transaction.
     */
    private int sellStock(Shop shop, ShopSlot slot) {
        if (!shop.isPlayerShop()) return Integer.MAX_VALUE;
        ItemStack template = slot.itemTemplate();
        try {
            int total = 0;
            for (InventoryEntry e : storage.inventory().findByShop(shop.id())) {
                if (ItemIdentity.sameItem(e.item(), template)) total += e.amount();
            }
            return total;
        } catch (SQLException ex) {
            LOG.warning("Stock pre-check failed for shop " + shop.id() + ": " + ex.getMessage());
            return Integer.MAX_VALUE;
        }
    }

    private void showConfirm(Player viewer, Shop shop, ShopSlot slot, TradeSide side, int packs) {
        int totalItems = packs * slot.unitAmount();
        // Freeze the price (snapshot) at confirm-open time per spec §12.3.2.
        // For player shops / when providers are disabled, this is just the
        // static slot price; for admin shops it runs the provider pipeline.
        PriceResolver.Resolution resolution = priceResolver.resolve(shop, slot, side, viewer, totalItems);
        BigDecimal unit = resolution.finalPrice();
        BigDecimal gross = unit.multiply(BigDecimal.valueOf(packs));
        BigDecimal fee = economy.computeFee(gross);
        BigDecimal payout = gross.subtract(fee);

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
        if (resolution.reason() != null) {
            body = body.append(Component.newline()).append(resolution.reason());
        }
        // Spec §12.3.2: surface the snapshot freeze time so the user can tell
        // when the price was locked in.
        body = body.append(Component.newline()).append(messages.get("trade.frozen-at",
                Placeholder.parsed("time", FROZEN_AT_FMT.format(Instant.now()))));
        Component title = messages.get(key + ".title");
        Component yes = messages.get(key + ".yes");
        Component no = messages.get(key + ".no");

        final Component finalBody = body;
        dialogs.confirmOnce(viewer, title, finalBody, yes, no,
                () -> execute(viewer, shop, slot, side, packs, resolution),
                () -> {});
    }

    private void execute(Player viewer, Shop shop, ShopSlot slot, TradeSide side,
                         int packs, PriceResolver.Resolution snapshot) {
        TradeRequest req = new TradeRequest(viewer, shop, slot, side, packs,
                snapshot.finalPrice(), snapshot.basePrice(), snapshot.resolvedBy());
        TradeResult result = tradeService.execute(req);
        if (result instanceof TradeResult.Success ok) {
            String key = ok.side() == TradeSide.SELL ? "trade.sold" : "trade.bought";
            viewer.sendMessage(messages.get(key,
                    Placeholder.component("item", displayName(slot)),
                    Placeholder.parsed("amount", Integer.toString(ok.amount())),
                    Placeholder.parsed("price", economy.format(ok.gross())),
                    Placeholder.parsed("fee", economy.format(ok.fee()))));
        } else if (result instanceof TradeResult.Failure fail) {
            viewer.sendMessage(messages.get(fail.messageKey(), fail.placeholders()));
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

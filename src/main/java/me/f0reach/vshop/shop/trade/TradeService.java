package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.api.event.ShopPreTransactionEvent;
import me.f0reach.vshop.api.event.ShopTransactionEvent;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Settles a single SELL or BUY trade. Runs on the main thread.
 *
 * The fund/inventory paths intentionally hold a DB transaction open while
 * doing Vault calls — Vault writes are not atomic with the DB, so the order is:
 *   1. open tx, lock + re-read state
 *   2. validate everything
 *   3. do Vault transfers (with best-effort refund on partial failure)
 *   4. apply DB mutations
 *   5. commit
 * A post-Vault SQL failure leaves the money moved but the DB un-updated — we
 * log loudly and roll back, hoping a follow-up audit can catch it.
 */
public final class TradeService {

    private static final Logger LOG = Logger.getLogger(TradeService.class.getName());

    private final StorageManager storage;
    private final EconomyService economy;
    private final PluginConfig config;
    private final TradeNotifier notifier;
    private final me.f0reach.vshop.shop.edit.ShopEditService editService;
    private final PriceResolver priceResolver;

    public TradeService(StorageManager storage, EconomyService economy, PluginConfig config, TradeNotifier notifier,
                        me.f0reach.vshop.shop.edit.ShopEditService editService, PriceResolver priceResolver) {
        this.storage = storage;
        this.economy = economy;
        this.config = config;
        this.notifier = notifier;
        this.editService = editService;
        this.priceResolver = priceResolver;
    }

    public TradeResult execute(TradeRequest req) {
        if (editService != null && editService.isEditing(req.shop().id())) {
            return new TradeResult.Failure("shop.edit.busy");
        }
        // Re-resolve the price right before settlement (spec §12.3.2). If it
        // has drifted beyond `economy.priceDriftTolerance` against the snapshot
        // taken at confirm-open time, kill the trade and ask the user to reopen.
        if (priceResolver != null) {
            PriceResolver.Resolution live = priceResolver.resolve(
                    req.shop(), req.slot(), req.side(), req.viewer(), req.totalItems());
            if (priceResolver.isDriftBeyondTolerance(req.unitPriceSnapshot(), live.finalPrice())) {
                return new TradeResult.Failure("trade.price-drift");
            }
        }
        // Let other plugins veto the trade before any money or items move.
        ShopPreTransactionEvent pre = new ShopPreTransactionEvent(req.shop(), req.slot(), req.side(),
                req.viewer(), req.totalItems(), req.unitPriceSnapshot());
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) {
            return new TradeResult.Failure("error.generic");
        }
        if (req.side() == TradeSide.SELL) return executeSell(req);
        if (req.side() == TradeSide.BUY) return executeBuy(req);
        return new TradeResult.Failure("error.generic");
    }

    // ---- SELL ----

    private TradeResult executeSell(TradeRequest req) {
        Shop shop = req.shop();
        if (shop.suspended()) return new TradeResult.Failure("shop.open.suspended");
        ShopSlot slot = req.slot();
        if (!slot.allowsSell()) return new TradeResult.Failure("trade.out-of-stock");

        Player buyer = req.viewer();
        int totalItems = req.totalItems();
        BigDecimal gross = economy.round(req.gross());
        BigDecimal fee = economy.computeFee(gross);
        BigDecimal net = gross.subtract(fee);

        Connection c = null;
        boolean buyerWithdrew = false;
        List<Deposit> deposits = new ArrayList<>();
        try {
            c = storage.dataSource().getConnection();
            c.setAutoCommit(false);

            // 1. Stock check
            if (shop.isPlayerShop()) {
                int stock = storage.inventory().sumMatchingTx(c, shop.id(), slot.itemTemplate());
                if (stock < totalItems) {
                    c.rollback();
                    return new TradeResult.Failure("trade.out-of-stock");
                }
            }

            // 2. Trade limit check
            UUID limitKey = limitKeyFor(slot, buyer.getUniqueId());
            if (!checkAndReserveLimit(c, slot, limitKey, totalItems)) {
                c.rollback();
                return new TradeResult.Failure("trade.limit-reached");
            }

            // 3. Buyer balance
            if (!economy.has(buyer, gross)) {
                c.rollback();
                return new TradeResult.Failure("trade.not-enough-money-buyer");
            }

            // 4. Co-owner shares (player shops only)
            List<CoOwner> coOwners = shop.isPlayerShop()
                    ? storage.coOwners().findByShop(shop.id())
                    : List.of();
            Map<UUID, BigDecimal> shares = shop.isPlayerShop()
                    ? ShareDistribution.distribute(coOwners, net, config.economy().fractionDigits(),
                            config.economy().roundingMode())
                    : Map.of();

            // 5. Vault: withdraw buyer
            EconomyResponse withdraw = economy.withdraw(buyer, gross);
            if (!withdraw.transactionSuccess()) {
                c.rollback();
                return new TradeResult.Failure("trade.not-enough-money-buyer");
            }
            buyerWithdrew = true;

            // 6. Vault: deposit each share recipient
            for (var e : shares.entrySet()) {
                OfflinePlayer recipient = Bukkit.getOfflinePlayer(e.getKey());
                EconomyResponse resp = economy.deposit(recipient, e.getValue());
                if (!resp.transactionSuccess()) {
                    refundAll(buyer, gross, deposits);
                    c.rollback();
                    return new TradeResult.Failure("error.generic");
                }
                deposits.add(new Deposit(recipient, e.getValue()));
            }

            // 7. DB: inventory + tx record + offline notifications
            if (shop.isPlayerShop()) {
                storage.inventory().removeMatchingTx(c, shop.id(), slot.itemTemplate(), totalItems);
            }

            TradeRecord rec = new TradeRecord(0, Instant.now(), shop.id(), slot.id(),
                    TradeSide.SELL, buyer.getUniqueId(),
                    shop.isPlayerShop() ? shop.ownerUuid() : null,
                    cloneAs(slot.itemTemplate(), 1), totalItems,
                    req.unitPriceSnapshot(), fee,
                    req.basePrice() != null ? req.basePrice() : slot.unitPrice(),
                    req.unitPriceSnapshot(),
                    req.resolvedBy());
            long txId = storage.transactions().insertTx(c, rec);
            final TradeRecord recForEvent = new TradeRecord(txId, rec.at(), rec.shopId(), rec.slotId(),
                    rec.side(), rec.buyerUuid(), rec.sellerUuid(), rec.itemSnapshot(), rec.amount(),
                    rec.unitPrice(), rec.fee(), rec.basePrice(), rec.finalPrice(), rec.resolvedBy());

            if (shop.isPlayerShop()) {
                queueOfflineNotifications(c, coOwners, shop.id(), txId, net);
            }

            c.commit();
            Bukkit.getPluginManager().callEvent(new ShopTransactionEvent(shop, recForEvent));

            // 8. Post-commit: deliver items + notify online owners
            giveItems(buyer, slot.itemTemplate(), totalItems);
            if (shop.isPlayerShop()) {
                notifier.notifyOnline(coOwners, shop, TradeSide.SELL, totalItems, net, slot.itemTemplate());
            }

            return new TradeResult.Success(TradeSide.SELL, totalItems, req.unitPriceSnapshot(),
                    gross, fee, net, txId);
        } catch (SQLException ex) {
            LOG.severe("SELL trade failed: " + ex.getMessage());
            ex.printStackTrace();
            if (c != null) try { c.rollback(); } catch (SQLException ignored) {}
            // Best-effort refund if we already moved money but the DB failed afterwards.
            if (buyerWithdrew) {
                for (Deposit d : deposits) {
                    economy.withdraw(d.recipient(), d.amount());
                }
                economy.deposit(buyer, gross);
            }
            return new TradeResult.Failure("error.generic");
        } finally {
            close(c);
        }
    }

    // ---- BUY ----

    private TradeResult executeBuy(TradeRequest req) {
        Shop shop = req.shop();
        if (shop.suspended()) return new TradeResult.Failure("shop.open.suspended");
        ShopSlot slot = req.slot();
        if (!slot.allowsBuy()) return new TradeResult.Failure("trade.buy-full");

        Player deliverer = req.viewer();
        int totalItems = req.totalItems();
        // Use the snapshot price agreed in the confirm dialog. The execute()
        // drift check above already guaranteed it's within tolerance of the
        // currently-resolved price.
        BigDecimal unit = req.unitPriceSnapshot();
        BigDecimal gross = economy.round(unit.multiply(BigDecimal.valueOf(req.packCount())));
        BigDecimal fee = economy.computeFee(gross);
        BigDecimal payoutToDeliverer = gross.subtract(fee);

        // Pre-validate: deliverer has the items
        if (!playerHasItems(deliverer, slot.itemTemplate(), totalItems)) {
            return new TradeResult.Failure("trade.out-of-stock");
        }

        Connection c = null;
        boolean ownerWithdrew = false;
        BigDecimal ownerCharged = BigDecimal.ZERO;
        boolean delivererPaid = false;
        try {
            c = storage.dataSource().getConnection();
            c.setAutoCommit(false);

            // 1. Trade limit
            UUID limitKey = limitKeyFor(slot, deliverer.getUniqueId());
            if (!checkAndReserveLimit(c, slot, limitKey, totalItems)) {
                c.rollback();
                return new TradeResult.Failure("trade.limit-reached");
            }

            // 2. Capacity check (lock the slot row)
            int currentCapacity = lockAndReadCapacity(c, slot.id());
            if (currentCapacity < totalItems) {
                c.rollback();
                return new TradeResult.Failure("trade.buy-full");
            }

            // 3. PRIMARY balance check (player shop)
            OfflinePlayer primary = null;
            if (shop.isPlayerShop()) {
                if (shop.ownerUuid() == null) {
                    c.rollback();
                    return new TradeResult.Failure("error.generic");
                }
                primary = Bukkit.getOfflinePlayer(shop.ownerUuid());
                if (!economy.has(primary, gross)) {
                    c.rollback();
                    return new TradeResult.Failure("trade.not-enough-money-owner");
                }
            }

            // 4. Vault: withdraw owner (player shop) / nothing (admin)
            if (primary != null) {
                EconomyResponse r = economy.withdraw(primary, gross);
                if (!r.transactionSuccess()) {
                    c.rollback();
                    return new TradeResult.Failure("trade.not-enough-money-owner");
                }
                ownerWithdrew = true;
                ownerCharged = gross;
            }

            // 5. Vault: deposit deliverer
            EconomyResponse dr = economy.deposit(deliverer, payoutToDeliverer);
            if (!dr.transactionSuccess()) {
                if (ownerWithdrew) economy.deposit(primary, ownerCharged);
                c.rollback();
                return new TradeResult.Failure("error.generic");
            }
            delivererPaid = true;

            // 6. Move items from deliverer to shop inventory (if player shop)
            removeItemsFromPlayer(deliverer, slot.itemTemplate(), totalItems);
            if (shop.isPlayerShop()) {
                storage.inventory().addAmountTx(c, shop.id(), slot.slotIndex(),
                        cloneAs(slot.itemTemplate(), 1), totalItems);
            }

            // 7. Decrement buyCapacity (slot + DB)
            int newCapacity = currentCapacity - totalItems;
            slot.setBuyCapacity(newCapacity);
            updateBuyCapacity(c, slot.id(), newCapacity);

            // 8. Insert tx record + notifications
            BigDecimal baseForRec = req.basePrice() != null
                    ? req.basePrice()
                    : (slot.buyUnitPrice() != null ? slot.buyUnitPrice() : slot.unitPrice());
            TradeRecord rec = new TradeRecord(0, Instant.now(), shop.id(), slot.id(),
                    TradeSide.BUY, shop.isPlayerShop() ? shop.ownerUuid() : null,
                    deliverer.getUniqueId(),
                    cloneAs(slot.itemTemplate(), 1), totalItems,
                    unit, fee, baseForRec, unit, req.resolvedBy());
            long txId = storage.transactions().insertTx(c, rec);
            final TradeRecord recForEvent = new TradeRecord(txId, rec.at(), rec.shopId(), rec.slotId(),
                    rec.side(), rec.buyerUuid(), rec.sellerUuid(), rec.itemSnapshot(), rec.amount(),
                    rec.unitPrice(), rec.fee(), rec.basePrice(), rec.finalPrice(), rec.resolvedBy());

            List<CoOwner> coOwners = shop.isPlayerShop()
                    ? storage.coOwners().findByShop(shop.id())
                    : List.of();
            if (shop.isPlayerShop()) {
                queueOfflineNotifications(c, coOwners, shop.id(), txId, gross);
            }

            c.commit();
            Bukkit.getPluginManager().callEvent(new ShopTransactionEvent(shop, recForEvent));

            if (shop.isPlayerShop()) {
                notifier.notifyOnline(coOwners, shop, TradeSide.BUY, totalItems, gross, slot.itemTemplate());
            }

            return new TradeResult.Success(TradeSide.BUY, totalItems, unit, gross, fee, payoutToDeliverer, txId);
        } catch (SQLException ex) {
            LOG.severe("BUY trade failed: " + ex.getMessage());
            ex.printStackTrace();
            if (c != null) try { c.rollback(); } catch (SQLException ignored) {}
            // Refund any money moves we already made.
            if (delivererPaid) economy.withdraw(deliverer, payoutToDeliverer);
            if (ownerWithdrew) economy.deposit(Bukkit.getOfflinePlayer(shop.ownerUuid()), ownerCharged);
            return new TradeResult.Failure("error.generic");
        } finally {
            close(c);
        }
    }

    // ---- shared helpers ----

    private UUID limitKeyFor(ShopSlot slot, UUID playerUuid) {
        return slot.limitScope() == me.f0reach.vshop.model.LimitScope.GLOBAL
                ? ShopLimitRepository.GLOBAL_KEY
                : playerUuid;
    }

    /**
     * Atomically validates and reserves the slot's trade-limit usage. Returns
     * false if the trade would push usage past the configured limit.
     */
    private boolean checkAndReserveLimit(Connection c, ShopSlot slot, UUID limitKey, int totalItems) throws SQLException {
        if (slot.tradeLimit() == null) return true;
        long now = Instant.now().toEpochMilli();
        var found = storage.limits().findTx(c, slot.id(), limitKey);
        int currentAmount = found.map(me.f0reach.vshop.model.TradeLimitUsage::amount).orElse(0);
        Instant windowStart = found.map(me.f0reach.vshop.model.TradeLimitUsage::windowStart).orElse(null);
        // Reset window if applicable.
        if (windowStart != null && slot.resetPeriod() != null) {
            long resetDeadline = windowStart.toEpochMilli() + slot.resetPeriod().toMillis();
            if (now >= resetDeadline) {
                currentAmount = 0;
                storage.limits().resetTx(c, slot.id(), limitKey, 0, now);
                windowStart = Instant.ofEpochMilli(now);
            }
        }
        if (currentAmount + totalItems > slot.tradeLimit()) return false;
        long windowMillis = (windowStart == null) ? now : windowStart.toEpochMilli();
        storage.limits().incrementTx(c, slot.id(), limitKey, totalItems, windowMillis);
        return true;
    }

    private void queueOfflineNotifications(Connection c, List<CoOwner> coOwners, UUID shopId,
                                           long txId, BigDecimal amount) throws SQLException {
        for (CoOwner co : coOwners) {
            if (co.role() == CoOwnerRole.STAFF) continue;
            OfflinePlayer p = Bukkit.getOfflinePlayer(co.playerUuid());
            if (p.isOnline()) continue;
            storage.notifications().queueTx(c, co.playerUuid(), shopId, txId, 1, amount);
        }
    }

    private int lockAndReadCapacity(Connection c, UUID slotId) throws SQLException {
        // Lock hint is only meaningful on MySQL; SQLite serializes writers anyway.
        String suffix = storage.type() == PluginConfig.StorageType.MYSQL ? " FOR UPDATE" : "";
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT buy_capacity FROM shop_slots WHERE id = ?" + suffix)) {
            ps.setString(1, slotId.toString());
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return 0;
                return rs.getInt(1);
            }
        }
    }

    private void updateBuyCapacity(Connection c, UUID slotId, int newCapacity) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE shop_slots SET buy_capacity = ? WHERE id = ?")) {
            ps.setInt(1, newCapacity);
            ps.setString(2, slotId.toString());
            ps.executeUpdate();
        }
    }

    private void refundAll(Player buyer, BigDecimal gross, List<Deposit> deposits) {
        for (Deposit d : deposits) economy.withdraw(d.recipient(), d.amount());
        economy.deposit(buyer, gross);
    }

    private ItemStack cloneAs(ItemStack template, int amount) {
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    private boolean playerHasItems(Player player, ItemStack template, int needed) {
        int found = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null) continue;
            if (ItemIdentity.sameItem(stack, template)) {
                found += stack.getAmount();
                if (found >= needed) return true;
            }
        }
        return false;
    }

    private void removeItemsFromPlayer(Player player, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null) continue;
            if (!ItemIdentity.sameItem(stack, template)) continue;
            int take = Math.min(stack.getAmount(), remaining);
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) contents[i] = null;
            remaining -= take;
        }
        player.getInventory().setStorageContents(contents);
    }

    private void giveItems(Player player, ItemStack template, int amount) {
        int max = Math.max(1, template.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int batch = Math.min(remaining, max);
            ItemStack stack = template.clone();
            stack.setAmount(batch);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack drop : leftover.values()) {
                if (drop == null) continue;
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            remaining -= batch;
        }
    }

    private void close(Connection c) {
        if (c == null) return;
        try {
            c.setAutoCommit(true);
            c.close();
        } catch (SQLException ignored) {}
    }

    private record Deposit(OfflinePlayer recipient, BigDecimal amount) {}
}

package me.f0reach.vshop.shop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.VaultEconomyAdapter;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.storage.ListingRepository;
import me.f0reach.vshop.storage.ShopInventoryRepository;
import me.f0reach.vshop.storage.ShopRepository;
import me.f0reach.vshop.storage.TransactionRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class ShopService {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final MessageManager messages;
    private final ShopRepository shopRepo;
    private final ListingRepository listingRepo;
    private final ShopInventoryRepository shopInventoryRepo;
    private final TransactionRepository txRepo;
    private final VaultEconomyAdapter economy;

    public ShopService(JavaPlugin plugin, PluginConfig config, MessageManager messages,
            ShopRepository shopRepo, ListingRepository listingRepo,
            ShopInventoryRepository shopInventoryRepo,
            TransactionRepository txRepo, VaultEconomyAdapter economy) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.shopRepo = shopRepo;
        this.listingRepo = listingRepo;
        this.shopInventoryRepo = shopInventoryRepo;
        this.txRepo = txRepo;
        this.economy = economy;
    }

    public int createPlayerShop(UUID villagerUuid, UUID ownerUuid, String world, double x, double y, double z)
            throws SQLException {
        return shopRepo.create(ShopType.PLAYER, villagerUuid, ownerUuid, world, x, y, z);
    }

    public int createAdminShop(UUID villagerUuid, String world, double x, double y, double z) throws SQLException {
        return shopRepo.create(ShopType.ADMIN, villagerUuid, null, world, x, y, z);
    }

    public Optional<Shop> getShopByVillager(UUID villagerUuid) throws SQLException {
        return shopRepo.findByVillagerUuid(villagerUuid);
    }

    public Optional<Shop> getShopById(int shopId) throws SQLException {
        return shopRepo.findById(shopId);
    }

    public int addListing(int shopId, int uiSlot, ListingMode mode, byte[] itemSerialized,
            double unitPrice, int tradeQuantity, int stock, int targetStock,
            int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException {
        int count = listingRepo.countByShopId(shopId);
        if (count >= config.getMaxTradeItemTypes()) {
            return -1; // limit exceeded
        }
        return listingRepo.create(shopId, uiSlot, mode, itemSerialized, unitPrice, tradeQuantity, stock, targetStock,
                cooldownSeconds, lifetimeLimitPerPlayer);
    }

    public List<Listing> getListingsForDisplay(Shop shop, boolean includeDisabled) throws SQLException {
        List<Listing> listings = listingRepo.findByShopId(shop.shopId());
        if (!includeDisabled) {
            listings = listings.stream().filter(Listing::enabled).toList();
        }

        if (shop.type() != ShopType.PLAYER) {
            return listings;
        }

        Map<Integer, ItemStack> storage = getShopInventoryContents(shop.shopId());
        List<Listing> adjusted = new ArrayList<>(listings.size());
        for (Listing listing : listings) {
            if (listing.mode() == ListingMode.SELL) {
                int realStock = countMatchingItems(storage, listing.itemSerialized());
                adjusted.add(new Listing(
                        listing.listingId(),
                        listing.shopId(),
                        listing.uiSlot(),
                        listing.mode(),
                        listing.itemSerialized(),
                        listing.unitPrice(),
                        listing.tradeQuantity(),
                        realStock,
                        listing.targetStock(),
                        listing.cooldownSeconds(),
                        listing.lifetimeLimitPerPlayer(),
                        listing.enabled(),
                        listing.updatedAt()));
            } else {
                adjusted.add(listing);
            }
        }
        return adjusted;
    }

    public Map<Integer, ItemStack> getShopInventoryContents(int shopId) throws SQLException {
        Map<Integer, byte[]> serialized = shopInventoryRepo.findByShopId(shopId);
        Map<Integer, ItemStack> contents = new HashMap<>();
        for (Map.Entry<Integer, byte[]> entry : serialized.entrySet()) {
            try {
                ItemStack item = ItemStack.deserializeBytes(entry.getValue());
                if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                    contents.put(entry.getKey(), item);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to deserialize shop inventory slot", e);
            }
        }
        return contents;
    }

    public void setShopInventorySlot(int shopId, int slot, ItemStack item) throws SQLException {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            shopInventoryRepo.deleteSlot(shopId, slot);
            return;
        }
        shopInventoryRepo.upsertSlot(shopId, slot, item.serializeAsBytes());
    }

    public ItemStack addItemToShopInventory(int shopId, ItemStack item) throws SQLException {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return null;
        }

        Map<Integer, ItemStack> storage = getShopInventoryContents(shopId);
        ItemStack remaining = item.clone();
        int maxStack = Math.max(1, remaining.getMaxStackSize());

        List<Integer> occupiedSlots = storage.entrySet().stream()
                .filter(e -> e.getValue().isSimilar(remaining) && e.getValue().getAmount() < maxStack)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        for (int slot : occupiedSlots) {
            if (remaining.getAmount() <= 0) {
                break;
            }
            ItemStack current = storage.get(slot);
            int space = maxStack - current.getAmount();
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, remaining.getAmount());
            current.setAmount(current.getAmount() + move);
            remaining.setAmount(remaining.getAmount() - move);
            setShopInventorySlot(shopId, slot, current);
        }

        int slot = 0;
        while (remaining.getAmount() > 0) {
            if (!storage.containsKey(slot)) {
                int place = Math.min(maxStack, remaining.getAmount());
                ItemStack placed = remaining.clone();
                placed.setAmount(place);
                setShopInventorySlot(shopId, slot, placed);
                storage.put(slot, placed);
                remaining.setAmount(remaining.getAmount() - place);
            }
            slot++;
        }

        return remaining.getAmount() > 0 ? remaining : null;
    }

    public enum TradeResult {
        SUCCESS, OUT_OF_STOCK, BALANCE_SHORTAGE, NO_ITEM, BUY_ORDER_FULL,
        OWNER_INSUFFICIENT, TRADE_FAILED, LISTING_NOT_FOUND,
        COOLDOWN_ACTIVE, LIFETIME_LIMIT_REACHED
    }

    public record TradeAccess(
            TradeResult blockedReason,
            int remainingCooldownSeconds,
            int remainingLifetimeTrades) {
        public boolean canTrade() {
            return blockedReason == null;
        }
    }

    public TradeAccess getTradeAccess(Player player, Listing listing) throws SQLException {
        if (listing.cooldownSeconds() <= 0 && listing.lifetimeLimitPerPlayer() <= 0) {
            return new TradeAccess(null, 0, Integer.MAX_VALUE);
        }
        Instant since = Instant.now().minusSeconds(listing.cooldownSeconds());
        int count = listing.cooldownSeconds() > 0
                ? txRepo.countTradesForPlayerSince(listing.listingId(), player.getUniqueId(), since)
                : txRepo.countTradesForPlayer(listing.listingId(), player.getUniqueId());

        int limit = (int) Math.max(1, listing.lifetimeLimitPerPlayer());

        if (listing.cooldownSeconds() > 0 && count >= limit) {
            Instant oldest = txRepo.findOldestTradeTimeForPlayer(
                    listing.listingId(), player.getUniqueId(), since)
                    .orElse(Instant.now());
            int remainingCooldown = (int) (oldest.plusSeconds(listing.cooldownSeconds()).getEpochSecond()
                    - Instant.now().getEpochSecond());
            return new TradeAccess(TradeResult.COOLDOWN_ACTIVE, remainingCooldown, limit - count);
        }

        if (listing.cooldownSeconds() == 0 && count >= limit) {
            return new TradeAccess(TradeResult.LIFETIME_LIMIT_REACHED, 0, 0);
        }

        return new TradeAccess(null, 0, limit - count);
    }

    public TradeResult previewTrade(Player player, Listing listing, Shop shop) throws SQLException {
        TradeAccess access = getTradeAccess(player, listing);
        if (!access.canTrade()) {
            return access.blockedReason();
        }

        int tradeQuantity = listing.tradeQuantity();
        double gross = listing.unitPrice();

        if (listing.mode() == ListingMode.SELL) {
            if (!economy.has(player.getUniqueId(), gross)) {
                return TradeResult.BALANCE_SHORTAGE;
            }
            if (shop.type() == ShopType.PLAYER) {
                int realStock = countMatchingItems(getShopInventoryContents(shop.shopId()), listing.itemSerialized());
                if (realStock < tradeQuantity) {
                    return TradeResult.OUT_OF_STOCK;
                }
            }
            return TradeResult.SUCCESS;
        }

        ItemStack template = ItemStack.deserializeBytes(listing.itemSerialized());
        template.setAmount(1);
        if (!player.getInventory().containsAtLeast(template, tradeQuantity)) {
            return TradeResult.NO_ITEM;
        }

        if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null && !economy.has(shop.ownerUuid(), gross)) {
            return TradeResult.OWNER_INSUFFICIENT;
        }

        if (shop.type() != ShopType.ADMIN && listing.stock() + tradeQuantity > listing.targetStock()) {
            return TradeResult.BUY_ORDER_FULL;
        }

        return TradeResult.SUCCESS;
    }

    /**
     * Execute a SELL trade: customer buys from shop.
     * Customer pays gross, fee deducted, net goes to owner (for player shops).
     */
    public TradeResult executeSellTrade(Player customer, Listing listing, Shop shop) {
        try {
            TradeAccess access = getTradeAccess(customer, listing);
            if (!access.canTrade()) {
                return access.blockedReason();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check trade restrictions", e);
            return TradeResult.TRADE_FAILED;
        }

        int tradeQuantity = listing.tradeQuantity();
        double gross = listing.unitPrice();
        double fee = gross * config.getFeeRate();
        double net = gross - fee;

        // Check customer balance
        if (!economy.has(customer.getUniqueId(), gross)) {
            return TradeResult.BALANCE_SHORTAGE;
        }

        try {
            ItemStack tradeItem;
            if (shop.type() == ShopType.PLAYER) {
                Optional<ItemStack> removed = removeMatchingFromShopInventory(shop.shopId(), listing.itemSerialized(),
                        tradeQuantity);
                if (removed.isEmpty()) {
                    return TradeResult.OUT_OF_STOCK;
                }
                tradeItem = removed.get();
            } else {
                if (shop.type() != ShopType.ADMIN && !listingRepo.decrementStock(listing.listingId(), tradeQuantity)) {
                    return TradeResult.OUT_OF_STOCK;
                }
                tradeItem = ItemStack.deserializeBytes(listing.itemSerialized());
                tradeItem.setAmount(tradeQuantity);
            }

            // Withdraw from customer
            if (!economy.withdraw(customer.getUniqueId(), gross)) {
                rollbackSellStock(shop, listing, tradeItem);
                return TradeResult.TRADE_FAILED;
            }

            // Deposit to owner (player shops only)
            if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
                if (!economy.deposit(shop.ownerUuid(), net)) {
                    // Rollback: refund customer, restore stock
                    economy.deposit(customer.getUniqueId(), gross);
                    rollbackSellStock(shop, listing, tradeItem);
                    return TradeResult.TRADE_FAILED;
                }
            }

            // Give item to customer
            customer.getInventory().addItem(tradeItem);

            // Record transaction
            txRepo.record(shop.shopId(), listing.listingId(), "PURCHASE",
                    customer.getUniqueId(), shop.ownerUuid(), tradeQuantity, gross, fee, net);

            return TradeResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Trade failed", e);
            return TradeResult.TRADE_FAILED;
        }
    }

    /**
     * Execute a BUY trade: customer delivers item to shop (procurement).
     * Shop owner pays gross, fee deducted, net goes to customer.
     */
    public TradeResult executeBuyTrade(Player customer, Listing listing, Shop shop) {
        try {
            TradeAccess access = getTradeAccess(customer, listing);
            if (!access.canTrade()) {
                return access.blockedReason();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check trade restrictions", e);
            return TradeResult.TRADE_FAILED;
        }

        int tradeQuantity = listing.tradeQuantity();
        double gross = listing.unitPrice();
        double fee = gross * config.getFeeRate();
        double net = gross - fee;

        // Check customer has the item
        ItemStack template = ItemStack.deserializeBytes(listing.itemSerialized());
        template.setAmount(1);
        if (!customer.getInventory().containsAtLeast(template, tradeQuantity)) {
            return TradeResult.NO_ITEM;
        }

        // Check owner balance (player shops only)
        if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
            if (!economy.has(shop.ownerUuid(), gross)) {
                return TradeResult.OWNER_INSUFFICIENT;
            }
        }

        try {
            // Admin shops have unlimited capacity
            if (shop.type() != ShopType.ADMIN && !listingRepo.incrementStock(listing.listingId(), tradeQuantity)) {
                return TradeResult.BUY_ORDER_FULL;
            }

            // Remove item from customer
            ItemStack tradedItem = template.clone();
            tradedItem.setAmount(tradeQuantity);
            customer.getInventory().removeItem(tradedItem);

            // Player shops store bought item in dedicated inventory
            if (shop.type() == ShopType.PLAYER) {
                addItemToShopInventory(shop.shopId(), tradedItem);
            }

            // Withdraw from owner (player shops only)
            if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
                if (!economy.withdraw(shop.ownerUuid(), gross)) {
                    rollbackBuyTradeCustomerItem(customer, tradedItem);
                    rollbackBuyStock(shop, listing, tradeQuantity);
                    if (shop.type() == ShopType.PLAYER) {
                        removeMatchingFromShopInventory(shop.shopId(), listing.itemSerialized(), tradeQuantity);
                    }
                    return TradeResult.TRADE_FAILED;
                }
            }

            // Deposit to customer
            if (!economy.deposit(customer.getUniqueId(), net)) {
                if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
                    economy.deposit(shop.ownerUuid(), gross);
                }
                rollbackBuyTradeCustomerItem(customer, tradedItem);
                rollbackBuyStock(shop, listing, tradeQuantity);
                if (shop.type() == ShopType.PLAYER) {
                    removeMatchingFromShopInventory(shop.shopId(), listing.itemSerialized(), tradeQuantity);
                }
                return TradeResult.TRADE_FAILED;
            }

            // Record transaction
            txRepo.record(shop.shopId(), listing.listingId(), "PROCUREMENT",
                    customer.getUniqueId(), shop.ownerUuid(), tradeQuantity, gross, fee, net);

            return TradeResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Trade failed", e);
            return TradeResult.TRADE_FAILED;
        }
    }

    private void rollbackSellStock(Shop shop, Listing listing, ItemStack tradeItem) {
        try {
            if (shop.type() == ShopType.PLAYER) {
                addItemToShopInventory(shop.shopId(), tradeItem);
            } else if (shop.type() != ShopType.ADMIN) {
                listingRepo.incrementStock(listing.listingId(), listing.tradeQuantity());
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rollback sell stock", e);
        }
    }

    private void rollbackBuyStock(Shop shop, Listing listing, int amount) {
        try {
            if (shop.type() != ShopType.ADMIN) {
                listingRepo.decrementStock(listing.listingId(), amount);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to rollback buy stock", e);
        }
    }

    private void rollbackBuyTradeCustomerItem(Player customer, ItemStack template) {
        customer.getInventory().addItem(template);
    }

    private Optional<ItemStack> removeMatchingFromShopInventory(int shopId, byte[] serializedTemplate, int amount)
            throws SQLException {
        Map<Integer, ItemStack> storage = getShopInventoryContents(shopId);
        ItemStack template = ItemStack.deserializeBytes(serializedTemplate);
        List<Map.Entry<Integer, ItemStack>> matchingSlots = storage.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .filter(entry -> entry.getValue().isSimilar(template))
                .toList();

        int available = matchingSlots.stream().mapToInt(entry -> entry.getValue().getAmount()).sum();
        if (available < amount) {
            return Optional.empty();
        }

        int remaining = amount;
        for (Map.Entry<Integer, ItemStack> entry : matchingSlots) {
            if (remaining <= 0) {
                break;
            }

            int slot = entry.getKey();
            ItemStack current = entry.getValue();
            int remove = Math.min(remaining, current.getAmount());
            int updatedAmount = current.getAmount() - remove;

            if (updatedAmount <= 0) {
                setShopInventorySlot(shopId, slot, null);
            } else {
                ItemStack updated = current.clone();
                updated.setAmount(updatedAmount);
                setShopInventorySlot(shopId, slot, updated);
            }
            remaining -= remove;
        }

        ItemStack removed = template.clone();
        removed.setAmount(amount);
        return Optional.of(removed);
    }

    private int countMatchingItems(Map<Integer, ItemStack> storage, byte[] serializedTemplate) {
        ItemStack template = ItemStack.deserializeBytes(serializedTemplate);
        int count = 0;
        for (ItemStack item : storage.values()) {
            if (item.isSimilar(template)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public ShopRepository getShopRepo() {
        return shopRepo;
    }

    public ListingRepository getListingRepo() {
        return listingRepo;
    }

    public TransactionRepository getTxRepo() {
        return txRepo;
    }

    public VaultEconomyAdapter getEconomy() {
        return economy;
    }
}

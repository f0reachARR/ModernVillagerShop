package me.f0reach.vshop.shop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.VaultEconomyAdapter;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.storage.ListingRepository;
import me.f0reach.vshop.storage.ShopRepository;
import me.f0reach.vshop.storage.TransactionRepository;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public final class ShopService {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final MessageManager messages;
    private final ShopRepository shopRepo;
    private final ListingRepository listingRepo;
    private final TransactionRepository txRepo;
    private final VaultEconomyAdapter economy;

    public ShopService(JavaPlugin plugin, PluginConfig config, MessageManager messages,
                       ShopRepository shopRepo, ListingRepository listingRepo,
                       TransactionRepository txRepo, VaultEconomyAdapter economy) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.shopRepo = shopRepo;
        this.listingRepo = listingRepo;
        this.txRepo = txRepo;
        this.economy = economy;
    }

    public int createPlayerShop(UUID villagerUuid, UUID ownerUuid, String world, double x, double y, double z) throws SQLException {
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
                          double unitPrice, int stock, int targetStock) throws SQLException {
        int count = listingRepo.countByShopId(shopId);
        if (count >= config.getMaxTradeItemTypes()) {
            return -1; // limit exceeded
        }
        return listingRepo.create(shopId, uiSlot, mode, itemSerialized, unitPrice, stock, targetStock);
    }

    public enum TradeResult {
        SUCCESS, OUT_OF_STOCK, BALANCE_SHORTAGE, NO_ITEM, BUY_ORDER_FULL,
        OWNER_INSUFFICIENT, TRADE_FAILED, LISTING_NOT_FOUND
    }

    /**
     * Execute a SELL trade: customer buys from shop.
     * Customer pays gross, fee deducted, net goes to owner (for player shops).
     */
    public TradeResult executeSellTrade(Player customer, Listing listing, Shop shop) {
        double gross = listing.unitPrice();
        double fee = gross * config.getFeeRate();
        double net = gross - fee;

        // Check customer balance
        if (!economy.has(customer.getUniqueId(), gross)) {
            return TradeResult.BALANCE_SHORTAGE;
        }

        try {
            // Admin shops have unlimited stock
            if (shop.type() != ShopType.ADMIN) {
                if (!listingRepo.decrementStock(listing.listingId())) {
                    return TradeResult.OUT_OF_STOCK;
                }
            }

            // Withdraw from customer
            if (!economy.withdraw(customer.getUniqueId(), gross)) {
                // Rollback stock
                if (shop.type() != ShopType.ADMIN) {
                    listingRepo.incrementStock(listing.listingId());
                }
                return TradeResult.TRADE_FAILED;
            }

            // Deposit to owner (player shops only)
            if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
                if (!economy.deposit(shop.ownerUuid(), net)) {
                    // Rollback: refund customer, restore stock
                    economy.deposit(customer.getUniqueId(), gross);
                    if (shop.type() != ShopType.ADMIN) {
                        listingRepo.incrementStock(listing.listingId());
                    }
                    return TradeResult.TRADE_FAILED;
                }
            }

            // Give item to customer
            ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
            item.setAmount(1);
            customer.getInventory().addItem(item);

            // Record transaction
            txRepo.record(shop.shopId(), listing.listingId(), "PURCHASE",
                    customer.getUniqueId(), shop.ownerUuid(), 1, gross, fee, net);

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
        double gross = listing.unitPrice();
        double fee = gross * config.getFeeRate();
        double net = gross - fee;

        // Check customer has the item
        ItemStack template = ItemStack.deserializeBytes(listing.itemSerialized());
        template.setAmount(1);
        if (!customer.getInventory().containsAtLeast(template, 1)) {
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
            if (shop.type() != ShopType.ADMIN) {
                if (!listingRepo.incrementStock(listing.listingId())) {
                    return TradeResult.BUY_ORDER_FULL;
                }
            }

            // Remove item from customer
            customer.getInventory().removeItem(template);

            // Withdraw from owner (player shops only)
            if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
                if (!economy.withdraw(shop.ownerUuid(), gross)) {
                    // Rollback: give item back, decrement stock
                    customer.getInventory().addItem(template);
                    if (shop.type() != ShopType.ADMIN) {
                        listingRepo.decrementStock(listing.listingId());
                    }
                    return TradeResult.TRADE_FAILED;
                }
            }

            // Deposit to customer
            if (!economy.deposit(customer.getUniqueId(), net)) {
                // Rollback
                if (shop.type() == ShopType.PLAYER && shop.ownerUuid() != null) {
                    economy.deposit(shop.ownerUuid(), gross);
                }
                customer.getInventory().addItem(template);
                if (shop.type() != ShopType.ADMIN) {
                    listingRepo.decrementStock(listing.listingId());
                }
                return TradeResult.TRADE_FAILED;
            }

            // Record transaction
            txRepo.record(shop.shopId(), listing.listingId(), "PROCUREMENT",
                    customer.getUniqueId(), shop.ownerUuid(), 1, gross, fee, net);

            return TradeResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Trade failed", e);
            return TradeResult.TRADE_FAILED;
        }
    }

    public ShopRepository getShopRepo() { return shopRepo; }
    public ListingRepository getListingRepo() { return listingRepo; }
    public TransactionRepository getTxRepo() { return txRepo; }
}

package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.InventoryEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the {@code shop_inventory} table. The "Tx" variants take a
 * caller-provided connection so {@link me.f0reach.vshop.shop.trade.TradeService}
 * can run the inventory mutation inside the same transaction as limits/history.
 */
public interface ShopInventoryRepository {

    List<InventoryEntry> findByShop(UUID shopId) throws SQLException;

    Optional<InventoryEntry> findSlot(UUID shopId, int slotIndex) throws SQLException;

    /** Insert or update a slot. Outside a caller transaction. */
    void upsert(InventoryEntry entry) throws SQLException;

    void delete(UUID shopId, int slotIndex) throws SQLException;

    /** Add to (or insert) a slot's amount, returning the new amount. Within tx. */
    int addAmountTx(Connection c, UUID shopId, int slotIndex, org.bukkit.inventory.ItemStack item, int delta) throws SQLException;

    /** Total amount of the slot matching itemTemplate (by isSimilar) across the shop's inventory. */
    int sumMatchingTx(Connection c, UUID shopId, org.bukkit.inventory.ItemStack itemTemplate) throws SQLException;

    /** Decrement matching items by deltaTotal across slots (largest first). Throws if not enough. */
    void removeMatchingTx(Connection c, UUID shopId, org.bukkit.inventory.ItemStack itemTemplate, int deltaTotal) throws SQLException;
}

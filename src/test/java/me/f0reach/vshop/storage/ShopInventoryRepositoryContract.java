package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.storage.mysql.MysqlShopInventoryRepository;
import me.f0reach.vshop.storage.repo.ShopInventoryRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopInventoryRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ShopInventoryRepositoryContract extends AbstractRepositoryContract {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    private ShopInventoryRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopInventoryRepository(dataSource())
                : new MysqlShopInventoryRepository(dataSource());
    }

    private ItemStack diamond(int amount) {
        return BukkitTestSupport.item(Material.DIAMOND, amount);
    }

    private ItemStack emerald(int amount) {
        return BukkitTestSupport.item(Material.EMERALD, amount);
    }

    @Test
    void upsertAndFindBySlot() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        repo.upsert(new InventoryEntry(shop, 0, diamond(1), 32));

        InventoryEntry got = repo.findSlot(shop, 0).orElseThrow();
        assertEquals(32, got.amount());
        assertEquals(Material.DIAMOND, got.item().getType());
    }

    @Test
    void upsertReplacesExistingRow() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        repo.upsert(new InventoryEntry(shop, 0, diamond(1), 10));
        repo.upsert(new InventoryEntry(shop, 0, emerald(1), 5));

        InventoryEntry got = repo.findSlot(shop, 0).orElseThrow();
        assertEquals(Material.EMERALD, got.item().getType());
        assertEquals(5, got.amount());
    }

    @Test
    void findByShopOrdersBySlotIndex() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        repo.upsert(new InventoryEntry(shop, 2, diamond(1), 1));
        repo.upsert(new InventoryEntry(shop, 0, diamond(1), 1));
        repo.upsert(new InventoryEntry(shop, 1, diamond(1), 1));

        List<InventoryEntry> all = repo.findByShop(shop);
        assertEquals(3, all.size());
        assertEquals(0, all.get(0).slotIndex());
        assertEquals(1, all.get(1).slotIndex());
        assertEquals(2, all.get(2).slotIndex());
    }

    @Test
    void deleteRemovesRow() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        repo.upsert(new InventoryEntry(shop, 0, diamond(1), 1));
        repo.delete(shop, 0);
        assertTrue(repo.findSlot(shop, 0).isEmpty());
    }

    @Test
    void addAmountInsertsNewRow() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            int result = repo.addAmountTx(c, shop, 5, diamond(1), 7);
            c.commit();
            assertEquals(7, result);
        }
        assertEquals(7, repo.findSlot(shop, 5).orElseThrow().amount());
    }

    @Test
    void addAmountAccumulatesOnExistingRow() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, diamond(1), 4);
            int result = repo.addAmountTx(c, shop, 0, diamond(1), 3);
            c.commit();
            assertEquals(7, result);
        }
    }

    @Test
    void addAmountDeletesRowWhenItReachesZero() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, diamond(1), 5);
            int result = repo.addAmountTx(c, shop, 0, diamond(1), -5);
            c.commit();
            assertEquals(0, result);
        }
        assertTrue(repo.findSlot(shop, 0).isEmpty());
    }

    @Test
    void addAmountRefusesToGoNegative() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, diamond(1), 3);
            assertThrows(SQLException.class,
                    () -> repo.addAmountTx(c, shop, 0, diamond(1), -4));
            c.rollback();
        }
    }

    @Test
    void sumMatchingAcrossSlots() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, diamond(1), 5);
            repo.addAmountTx(c, shop, 1, diamond(1), 8);
            repo.addAmountTx(c, shop, 2, emerald(1), 100);
            c.commit();

            assertEquals(13, repo.sumMatchingTx(c, shop, diamond(1)));
            assertEquals(100, repo.sumMatchingTx(c, shop, emerald(1)));
        }
    }

    @Test
    void removeMatchingDrainsLargestSlotsFirst() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, diamond(1), 5);
            repo.addAmountTx(c, shop, 1, diamond(1), 20);
            c.commit();

            c.setAutoCommit(false);
            repo.removeMatchingTx(c, shop, diamond(1), 22);
            c.commit();
        }
        // 22 removed from {0:5, 1:20}: largest first → drains slot 1 entirely
        // (removes 20), then takes 2 more from slot 0, leaving 3.
        assertTrue(repo.findSlot(shop, 1).isEmpty());
        assertEquals(3, repo.findSlot(shop, 0).orElseThrow().amount());
    }

    @Test
    void removeMatchingThrowsWhenShort() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, diamond(1), 3);
            c.commit();
        }

        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            assertThrows(SQLException.class,
                    () -> repo.removeMatchingTx(c, shop, diamond(1), 4));
            c.rollback();
        }
    }

    @Test
    void removeMatchingIgnoresNonMatchingItem() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.addAmountTx(c, shop, 0, emerald(1), 50);
            c.commit();
        }
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            assertThrows(SQLException.class,
                    () -> repo.removeMatchingTx(c, shop, diamond(1), 1));
            c.rollback();
        }
        // Emerald row should remain untouched.
        assertEquals(50, repo.findSlot(shop, 0).orElseThrow().amount());
    }

    @Test
    void removeMatchingZeroDeltaIsNoOp() throws SQLException {
        ShopInventoryRepository repo = repository();
        UUID shop = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.removeMatchingTx(c, shop, diamond(1), 0);
            c.commit();
        }
        assertFalse(repo.findSlot(shop, 0).isPresent());
    }
}

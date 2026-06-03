package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.TradeLimitUsage;
import me.f0reach.vshop.storage.mysql.MysqlShopLimitRepository;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopLimitRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ShopLimitRepositoryContract extends AbstractRepositoryContract {

    private ShopLimitRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopLimitRepository(dataSource())
                : new MysqlShopLimitRepository(dataSource());
    }

    @Test
    void incrementInsertsFirstEntry() throws SQLException {
        ShopLimitRepository repo = repository();
        UUID slot = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        long windowStart = 1_700_000_000_000L;

        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            TradeLimitUsage usage = repo.incrementTx(c, slot, player, 3, windowStart);
            c.commit();
            assertEquals(3, usage.amount());
            assertEquals(windowStart, usage.windowStart().toEpochMilli());
        }

        try (Connection c = dataSource().getConnection()) {
            Optional<TradeLimitUsage> found = repo.findTx(c, slot, player);
            assertTrue(found.isPresent());
            assertEquals(3, found.get().amount());
        }
    }

    @Test
    void incrementAccumulatesPreservingWindowStart() throws SQLException {
        ShopLimitRepository repo = repository();
        UUID slot = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        long originalWindow = 1_700_000_000_000L;
        long laterWindow = 1_800_000_000_000L;

        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.incrementTx(c, slot, player, 2, originalWindow);
            TradeLimitUsage updated = repo.incrementTx(c, slot, player, 5, laterWindow);
            c.commit();
            assertEquals(7, updated.amount());
            // window_start MUST NOT advance on subsequent increments — it marks
            // the *start* of the current limit window.
            assertEquals(originalWindow, updated.windowStart().toEpochMilli());
        }
    }

    @Test
    void resetReplacesAmountAndWindow() throws SQLException {
        ShopLimitRepository repo = repository();
        UUID slot = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.incrementTx(c, slot, player, 8, 100);
            repo.resetTx(c, slot, player, 1, 9_999);
            c.commit();
            Optional<TradeLimitUsage> after = repo.findTx(c, slot, player);
            assertTrue(after.isPresent());
            assertEquals(1, after.get().amount());
            assertEquals(9_999, after.get().windowStart().toEpochMilli());
        }
    }

    @Test
    void globalKeySharedAcrossPlayers() throws SQLException {
        ShopLimitRepository repo = repository();
        UUID slot = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.incrementTx(c, slot, ShopLimitRepository.GLOBAL_KEY, 4, 0);
            repo.incrementTx(c, slot, ShopLimitRepository.GLOBAL_KEY, 6, 0);
            c.commit();
            assertEquals(10, repo.findTx(c, slot, ShopLimitRepository.GLOBAL_KEY).orElseThrow().amount());
        }
    }

    @Test
    void deleteAllForSlotRemovesAllRows() throws SQLException {
        ShopLimitRepository repo = repository();
        UUID slot = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.incrementTx(c, slot, p1, 1, 0);
            repo.incrementTx(c, slot, p2, 1, 0);
            c.commit();
        }

        repo.deleteAllForSlot(slot);

        try (Connection c = dataSource().getConnection()) {
            assertTrue(repo.findTx(c, slot, p1).isEmpty());
            assertTrue(repo.findTx(c, slot, p2).isEmpty());
        }
    }
}

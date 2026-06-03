package me.f0reach.vshop.storage;

import me.f0reach.vshop.storage.mysql.MysqlShopNotificationRepository;
import me.f0reach.vshop.storage.repo.ShopNotificationRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopNotificationRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ShopNotificationRepositoryContract extends AbstractRepositoryContract {

    private ShopNotificationRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopNotificationRepository(dataSource())
                : new MysqlShopNotificationRepository(dataSource());
    }

    @Test
    void queuesAndReadsUndelivered() throws SQLException {
        ShopNotificationRepository repo = repository();
        UUID player = UUID.randomUUID();
        UUID shop = UUID.randomUUID();

        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.queueTx(c, player, shop, 42L, 3, new BigDecimal("123.45"));
            repo.queueTx(c, player, null, null, 1, new BigDecimal("9.99"));
            c.commit();
        }

        List<ShopNotificationRepository.Pending> pending = repo.findUndelivered(player);
        assertEquals(2, pending.size());
        ShopNotificationRepository.Pending first = pending.get(0);
        assertEquals(shop, first.shopId());
        assertEquals(42L, first.transactionId());
        assertEquals(3, first.count());
        assertEquals(0, first.totalAmount().compareTo(new BigDecimal("123.45")));

        ShopNotificationRepository.Pending second = pending.get(1);
        assertNull(second.shopId());
        assertNull(second.transactionId());
    }

    @Test
    void markDeliveredHidesFromUndelivered() throws SQLException {
        ShopNotificationRepository repo = repository();
        UUID player = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.queueTx(c, player, UUID.randomUUID(), 1L, 1, BigDecimal.ONE);
            repo.queueTx(c, player, UUID.randomUUID(), 2L, 1, BigDecimal.ONE);
            c.commit();
        }

        List<Long> ids = new ArrayList<>();
        for (var p : repo.findUndelivered(player)) ids.add(p.id());
        assertEquals(2, ids.size());

        repo.markDelivered(List.of(ids.get(0)));

        List<ShopNotificationRepository.Pending> remaining = repo.findUndelivered(player);
        assertEquals(1, remaining.size());
        assertEquals(ids.get(1), remaining.get(0).id());
    }

    @Test
    void markDeliveredOnEmptyIsNoOp() throws SQLException {
        repository().markDelivered(List.of());
    }

    @Test
    void undeliveredFiltersByPlayer() throws SQLException {
        ShopNotificationRepository repo = repository();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            repo.queueTx(c, a, null, null, 1, BigDecimal.ONE);
            repo.queueTx(c, b, null, null, 1, BigDecimal.ONE);
            c.commit();
        }

        assertEquals(1, repo.findUndelivered(a).size());
        assertEquals(1, repo.findUndelivered(b).size());
        assertTrue(repo.findUndelivered(UUID.randomUUID()).isEmpty());
    }
}

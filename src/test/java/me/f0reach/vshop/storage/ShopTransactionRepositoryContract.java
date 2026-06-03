package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.mysql.MysqlShopTransactionRepository;
import me.f0reach.vshop.storage.repo.ShopTransactionRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopTransactionRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ShopTransactionRepositoryContract extends AbstractRepositoryContract {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    private ShopTransactionRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopTransactionRepository(dataSource())
                : new MysqlShopTransactionRepository(dataSource());
    }

    private ItemStack diamond() {
        return BukkitTestSupport.item(Material.DIAMOND, 1);
    }

    private long insert(ShopTransactionRepository repo, TradeRecord rec) throws SQLException {
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            long id = repo.insertTx(c, rec);
            c.commit();
            return id;
        }
    }

    private TradeRecord sell(UUID shop, UUID seller, UUID buyer, Instant at, BigDecimal unit, int amount) {
        return new TradeRecord(0L, at, shop, UUID.randomUUID(), TradeSide.SELL,
                buyer, seller, diamond(), amount, unit, BigDecimal.ZERO,
                null, null, null);
    }

    private TradeRecord buy(UUID shop, UUID buyer, UUID seller, Instant at, BigDecimal unit, int amount,
                            BigDecimal fee) {
        return new TradeRecord(0L, at, shop, UUID.randomUUID(), TradeSide.BUY,
                buyer, seller, diamond(), amount, unit, fee,
                unit, unit, "fixed");
    }

    @Test
    void insertAssignsGeneratedKey() throws SQLException {
        ShopTransactionRepository repo = repository();
        long id = insert(repo, sell(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), new BigDecimal("10.0000"), 1));
        assertTrue(id > 0);
    }

    @Test
    void findByShopReturnsNewestFirst() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        Instant older = Instant.ofEpochSecond(1_000_000L);
        Instant newer = Instant.ofEpochSecond(2_000_000L);
        long oldId = insert(repo, sell(shop, seller, buyer, older, BigDecimal.ONE, 1));
        long newId = insert(repo, sell(shop, seller, buyer, newer, BigDecimal.ONE, 1));

        List<TradeRecord> page = repo.findByShop(shop, 10, 0);
        assertEquals(2, page.size());
        assertEquals(newId, page.get(0).id());
        assertEquals(oldId, page.get(1).id());
    }

    @Test
    void findByPlayerMatchesEitherSide() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        insert(repo, sell(shop, alice, bob, Instant.ofEpochSecond(1L), BigDecimal.ONE, 1));
        insert(repo, sell(shop, bob, alice, Instant.ofEpochSecond(2L), BigDecimal.ONE, 1));

        assertEquals(2, repo.findByPlayer(alice, 10, 0).size());
        assertEquals(2, repo.findByPlayer(bob, 10, 0).size());
        assertEquals(0, repo.findByPlayer(UUID.randomUUID(), 10, 0).size());
    }

    @Test
    void aggregateSumsByTradeSide() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        insert(repo, sell(shop, seller, buyer, Instant.now(), new BigDecimal("10.0000"), 2));
        insert(repo, sell(shop, seller, buyer, Instant.now(), new BigDecimal("20.0000"), 1));
        insert(repo, buy(shop, buyer, seller, Instant.now(), new BigDecimal("5.0000"), 3, new BigDecimal("1.5000")));

        ShopTransactionRepository.AggregateStats stats = repo.aggregate(shop);
        assertEquals(2, stats.sellCount());
        assertEquals(1, stats.buyCount());
        // 10*2 + 20*1 = 40
        assertEquals(0, stats.totalSalesValue().compareTo(new BigDecimal("40.0000")));
        // 5*3 = 15
        assertEquals(0, stats.totalBuyValue().compareTo(new BigDecimal("15.0000")));
        assertEquals(0, stats.totalFees().compareTo(new BigDecimal("1.5000")));
    }

    @Test
    void countFilteredHonorsTimeWindow() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        Instant t0 = Instant.ofEpochMilli(100_000);
        Instant t1 = Instant.ofEpochMilli(200_000);
        Instant t2 = Instant.ofEpochMilli(300_000);
        insert(repo, sell(shop, UUID.randomUUID(), UUID.randomUUID(), t0, BigDecimal.ONE, 1));
        insert(repo, sell(shop, UUID.randomUUID(), UUID.randomUUID(), t1, BigDecimal.ONE, 1));
        insert(repo, sell(shop, UUID.randomUUID(), UUID.randomUUID(), t2, BigDecimal.ONE, 1));

        // [t1, t2): only the middle row should match.
        var filter = new ShopTransactionRepository.HistoryFilter(shop, null, null, t1, t2);
        assertEquals(1, repo.countFiltered(filter));
        assertEquals(1, repo.findFiltered(filter, 10, 0).size());
    }

    @Test
    void findFilteredHonorsSideAndPlayer() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        insert(repo, sell(shop, alice, bob, Instant.ofEpochMilli(1_000), BigDecimal.ONE, 1));
        insert(repo, buy(shop, alice, bob, Instant.ofEpochMilli(2_000), BigDecimal.ONE, 1, BigDecimal.ZERO));

        var sellOnly = new ShopTransactionRepository.HistoryFilter(shop, alice, TradeSide.SELL, null, null);
        List<TradeRecord> sells = repo.findFiltered(sellOnly, 10, 0);
        assertEquals(1, sells.size());
        assertEquals(TradeSide.SELL, sells.get(0).side());
    }

    @Test
    void recentByDayBucketsByUtcDay() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        Instant now = Instant.now();
        // Two entries today (same bucket) and one yesterday (different bucket).
        insert(repo, sell(shop, UUID.randomUUID(), UUID.randomUUID(), now, new BigDecimal("3"), 2));
        insert(repo, sell(shop, UUID.randomUUID(), UUID.randomUUID(), now.minusSeconds(60), new BigDecimal("3"), 1));
        insert(repo, sell(shop, UUID.randomUUID(), UUID.randomUUID(),
                now.minus(Duration.ofDays(1)), new BigDecimal("4"), 1));

        List<ShopTransactionRepository.DailyBucket> buckets = repo.recentByDay(shop, 3, null);
        assertEquals(2, buckets.size());
        // Buckets are returned in chronological order via TreeMap.
        long today = (now.toEpochMilli() / 86_400_000L) * 86_400_000L;
        assertTrue(buckets.get(buckets.size() - 1).dayStart().toEpochMilli() == today);
    }

    @Test
    void resolvedByAndNullFieldsRoundTrip() throws SQLException {
        ShopTransactionRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        UUID seller = UUID.randomUUID();

        // null base/final/resolvedBy.
        long sellId = insert(repo, sell(shop, seller, buyer, Instant.ofEpochMilli(1), BigDecimal.ONE, 1));
        // populated base/final/resolvedBy.
        long buyId = insert(repo, buy(shop, buyer, seller, Instant.ofEpochMilli(2),
                new BigDecimal("2.0000"), 1, new BigDecimal("0.1000")));

        List<TradeRecord> rows = repo.findByShop(shop, 10, 0);
        TradeRecord byBuy = rows.stream().filter(r -> r.id() == buyId).findFirst().orElseThrow();
        TradeRecord bySell = rows.stream().filter(r -> r.id() == sellId).findFirst().orElseThrow();
        assertNull(bySell.basePrice());
        assertNull(bySell.finalPrice());
        assertNull(bySell.resolvedBy());
        assertNotNull(byBuy.basePrice());
        assertEquals("fixed", byBuy.resolvedBy());
    }
}

package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.PlayerCacheEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.mysql.MysqlCoOwnerRepository;
import me.f0reach.vshop.storage.mysql.MysqlPlayerCacheRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopTransactionRepository;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import me.f0reach.vshop.storage.repo.PlayerCacheRepository;
import me.f0reach.vshop.storage.repo.ShopRepository;
import me.f0reach.vshop.storage.repo.ShopTransactionRepository;
import me.f0reach.vshop.storage.sqlite.SqliteCoOwnerRepository;
import me.f0reach.vshop.storage.sqlite.SqlitePlayerCacheRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopTransactionRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks down timezone-sensitive behaviour across both backends. Every timestamp
 * column is stored as a {@code BIGINT} epoch-millis value, so the round-trip
 * must not drift even when the JVM default timezone is far from UTC. The tests
 * temporarily flip {@link TimeZone#setDefault(TimeZone)} to Asia/Tokyo (UTC+9)
 * and Pacific/Honolulu (UTC-10) to catch any code path that accidentally
 * formats through {@link LocalDateTime} or a JDBC {@code Timestamp}.
 */
public abstract class TimezoneSafetyContract extends AbstractRepositoryContract {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    private TimeZone originalTz;

    @BeforeEach
    void recordOriginalTz() {
        originalTz = TimeZone.getDefault();
    }

    @AfterEach
    void restoreTz() {
        TimeZone.setDefault(originalTz);
    }

    private ShopRepository shops() {
        return backend() == Backend.SQLITE
                ? new SqliteShopRepository(dataSource())
                : new MysqlShopRepository(dataSource());
    }

    private CoOwnerRepository coOwners() {
        return backend() == Backend.SQLITE
                ? new SqliteCoOwnerRepository(dataSource())
                : new MysqlCoOwnerRepository(dataSource());
    }

    private PlayerCacheRepository playerCache() {
        return backend() == Backend.SQLITE
                ? new SqlitePlayerCacheRepository(dataSource())
                : new MysqlPlayerCacheRepository(dataSource());
    }

    private ShopTransactionRepository transactions() {
        return backend() == Backend.SQLITE
                ? new SqliteShopTransactionRepository(dataSource())
                : new MysqlShopTransactionRepository(dataSource());
    }

    @Test
    void shopTimestampsRoundTripUnderJst() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

        UUID id = UUID.randomUUID();
        // Pick an Instant deep inside a JST "tomorrow" but UTC "today" — the
        // local-date interpretation would yield a different epoch-millis.
        Instant created = LocalDateTime.of(2026, 6, 3, 23, 30, 15, 0)
                .atZone(ZoneOffset.UTC).toInstant();
        Instant updated = created.plusSeconds(3_600 * 8); // crosses midnight JST

        Shop shop = new Shop(id, ShopType.PLAYER, UUID.randomUUID(),
                new ShopLocation(UUID.randomUUID(), 0, 64, 0, 0, 0),
                null, null, "TZ", false, 1, created, updated);
        shops().insert(shop);

        Shop loaded = shops().findById(id).orElseThrow();
        // Must match to the *millisecond*. Any TZ-aware conversion would shift
        // by 9 * 3600 * 1000 here.
        assertEquals(created.toEpochMilli(), loaded.createdAt().toEpochMilli());
        assertEquals(updated.toEpochMilli(), loaded.updatedAt().toEpochMilli());
    }

    @Test
    void shopTimestampsRoundTripUnderNegativeOffset() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));

        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T05:00:00Z"); // 1899-12-31 19:00 HST
        Shop shop = new Shop(id, ShopType.ADMIN, null,
                new ShopLocation(UUID.randomUUID(), 0, 0, 0, 0, 0),
                null, null, "TZ-", false, 1, created, created);
        shops().insert(shop);

        Shop loaded = shops().findById(id).orElseThrow();
        assertEquals(created.toEpochMilli(), loaded.createdAt().toEpochMilli());
    }

    @Test
    void coOwnerAddedAtSurvivesTzFlip() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

        UUID shop = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Instant addedAt = Instant.parse("2026-06-02T15:30:00Z");
        coOwners().upsert(new CoOwner(shop, player, CoOwnerRole.PRIMARY,
                new BigDecimal("100.00"), addedAt, null));

        List<CoOwner> rows = coOwners().findByShop(shop);
        assertEquals(1, rows.size());
        assertEquals(addedAt.toEpochMilli(), rows.get(0).addedAt().toEpochMilli());
    }

    @Test
    void playerCacheRetainsExactInstantsAcrossTzes() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

        UUID id = UUID.randomUUID();
        Instant texAt = Instant.parse("2026-06-03T18:45:30.123Z");
        Instant lastSeen = Instant.parse("2026-06-04T00:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-04T00:00:00.500Z");

        playerCache().upsert(new PlayerCacheEntry(id, "Tz", "tz",
                "tex", "sig", texAt, lastSeen, updatedAt));

        PlayerCacheEntry got = playerCache().findByUuid(id).orElseThrow();
        assertEquals(texAt.toEpochMilli(), got.textureUpdatedAt().toEpochMilli());
        assertEquals(lastSeen.toEpochMilli(), got.lastSeen().toEpochMilli());
        assertEquals(updatedAt.toEpochMilli(), got.updatedAt().toEpochMilli());
    }

    @Test
    void transactionOccurredAtRoundTripsUnderJst() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

        UUID shop = UUID.randomUUID();
        Instant at = Instant.parse("2026-06-03T15:00:00.789Z");
        TradeRecord rec = new TradeRecord(0L, at, shop, UUID.randomUUID(),
                TradeSide.SELL, UUID.randomUUID(), UUID.randomUUID(),
                BukkitTestSupport.item(Material.DIAMOND, 1),
                1, BigDecimal.ONE, BigDecimal.ZERO, null, null, null);

        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            transactions().insertTx(c, rec);
            c.commit();
        }

        TradeRecord loaded = transactions().findByShop(shop, 10, 0).get(0);
        assertEquals(at.toEpochMilli(), loaded.at().toEpochMilli());
    }

    @Test
    void recentByDayBucketsAreUtcAlignedRegardlessOfJvmTz() throws SQLException {
        // Bucket math (epochMillis / 86_400_000) * 86_400_000 anchors days to
        // UTC midnight. Verify a row inserted *near* a JST midnight still ends
        // up in the UTC-day bucket the code intends, not a JST-day bucket.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

        UUID shop = UUID.randomUUID();
        // 2026-06-03 14:00 UTC = 2026-06-03 23:00 JST → both UTC and JST "today".
        Instant inDayUtc = Instant.parse("2026-06-03T14:00:00Z");
        // 2026-06-03 16:00 UTC = 2026-06-04 01:00 JST → next JST day, same UTC day.
        Instant crossesJstMidnight = Instant.parse("2026-06-03T16:00:00Z");
        // 2026-06-04 02:00 UTC = 2026-06-04 11:00 JST → next UTC and JST day.
        Instant nextUtcDay = Instant.parse("2026-06-04T02:00:00Z");

        for (Instant at : List.of(inDayUtc, crossesJstMidnight, nextUtcDay)) {
            TradeRecord rec = new TradeRecord(0L, at, shop, UUID.randomUUID(),
                    TradeSide.SELL, UUID.randomUUID(), UUID.randomUUID(),
                    BukkitTestSupport.item(Material.DIAMOND, 1),
                    1, BigDecimal.ONE, BigDecimal.ZERO, null, null, null);
            try (Connection c = dataSource().getConnection()) {
                c.setAutoCommit(false);
                transactions().insertTx(c, rec);
                c.commit();
            }
        }

        // Look back far enough to include both rows from the perspective of
        // "now". recentByDay's window is relative to Instant.now(), so we use
        // a generous span; the test only cares about bucketing.
        long daysBack = java.time.Duration.between(inDayUtc, Instant.now()).toDays() + 2;
        List<ShopTransactionRepository.DailyBucket> buckets =
                transactions().recentByDay(shop, (int) Math.max(daysBack, 2), null);

        // Two UTC days were touched (2026-06-03 and 2026-06-04) — *not three*,
        // which is what a JST-aware bucketing would have produced.
        long distinctDays = buckets.stream().map(b -> b.dayStart().toEpochMilli()).distinct().count();
        assertEquals(2, distinctDays,
                "buckets should be aligned to UTC days, saw " + buckets);

        long dayA = bucketKey(inDayUtc);
        long dayB = bucketKey(nextUtcDay);
        // dayStart values must equal the UTC midnight of those instants.
        assertTrue(buckets.stream().anyMatch(b -> b.dayStart().toEpochMilli() == dayA));
        assertTrue(buckets.stream().anyMatch(b -> b.dayStart().toEpochMilli() == dayB));
    }

    @Test
    void dailyBucketDayStartAlwaysAtUtcMidnight() throws SQLException {
        // Whatever the JVM TZ, recentByDay's bucket key must land on a UTC
        // midnight — divisible cleanly by 86_400_000.
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));

        UUID shop = UUID.randomUUID();
        TradeRecord rec = new TradeRecord(0L, Instant.now(), shop, UUID.randomUUID(),
                TradeSide.SELL, UUID.randomUUID(), UUID.randomUUID(),
                BukkitTestSupport.item(Material.DIAMOND, 1),
                1, BigDecimal.ONE, BigDecimal.ZERO, null, null, null);
        try (Connection c = dataSource().getConnection()) {
            c.setAutoCommit(false);
            transactions().insertTx(c, rec);
            c.commit();
        }

        List<ShopTransactionRepository.DailyBucket> buckets = transactions().recentByDay(shop, 2, null);
        assertEquals(1, buckets.size());
        long dayStart = buckets.get(0).dayStart().toEpochMilli();
        assertEquals(0, dayStart % 86_400_000L,
                "dayStart must be a UTC midnight (was " + dayStart + ")");
        // And the instant matches the UTC start of "today" relative to the row's millis.
        ZoneId utc = ZoneOffset.UTC;
        Instant expectedMidnight = Instant.now().atZone(utc).toLocalDate().atStartOfDay(utc).toInstant();
        assertEquals(expectedMidnight.toEpochMilli(), dayStart);
    }

    @Test
    void localeIndependentEnumStorage() throws SQLException {
        // Some locales (notably tr_TR) capitalize ASCII letters differently.
        // Verify enum/string handling is locale-agnostic by flipping the default
        // locale while we round-trip a row whose columns contain enum names.
        Locale originalLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));

            UUID id = UUID.randomUUID();
            Shop shop = new Shop(id, ShopType.PLAYER, UUID.randomUUID(),
                    new ShopLocation(UUID.randomUUID(), 0, 64, 0, 0, 0),
                    null, null, "i̇shop", false, 1, Instant.now(), Instant.now());
            shops().insert(shop);

            Shop loaded = shops().findById(id).orElseThrow();
            assertNotNull(loaded);
            assertEquals(ShopType.PLAYER, loaded.type());
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static long bucketKey(Instant at) {
        return (at.toEpochMilli() / 86_400_000L) * 86_400_000L;
    }
}

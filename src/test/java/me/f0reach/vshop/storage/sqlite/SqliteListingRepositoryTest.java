package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.model.ListingWithAccess;
import me.f0reach.vshop.model.ListingMode;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.model.TradeAccessBlockReason;
import me.f0reach.vshop.storage.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteListingRepositoryTest {
    private static final DateTimeFormatter SQLITE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void aggregatesTradeAccessForShopListingsWithoutNPlusOne() throws Exception {
        SqliteTestDatabase database = SqliteTestDatabase.create(tempDir, "display-access.db");
        ConnectionProvider provider = database.connectionProvider();
        SqliteShopRepository shopRepository = new SqliteShopRepository(provider);
        SqliteListingRepository listingRepository = new SqliteListingRepository(provider);

        int shopId = shopRepository.create(
                ShopType.PLAYER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "world",
                0,
                64,
                0
        );
        int otherShopId = shopRepository.create(
                ShopType.PLAYER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "world",
                10,
                64,
                10
        );

        int unrestrictedListingId = listingRepository.create(
                shopId, 0, ListingMode.SELL, new byte[] {1}, 10.0, 1, 10, 10, 0, 0);
        int cooldownBlockedListingId = listingRepository.create(
                shopId, 1, ListingMode.SELL, new byte[] {2}, 10.0, 1, 10, 10, 60, 2);
        int cooldownAvailableListingId = listingRepository.create(
                shopId, 2, ListingMode.SELL, new byte[] {3}, 10.0, 1, 10, 10, 60, 3);
        int lifetimeBlockedListingId = listingRepository.create(
                shopId, 3, ListingMode.SELL, new byte[] {4}, 10.0, 1, 10, 10, 0, 2);
        int otherShopListingId = listingRepository.create(
                otherShopId, 0, ListingMode.SELL, new byte[] {5}, 10.0, 1, 10, 10, 60, 1);

        UUID buyerUuid = UUID.randomUUID();
        UUID otherBuyerUuid = UUID.randomUUID();
        Instant now = Instant.parse("2026-03-08T00:00:00Z");

        insertTransaction(provider, shopId, cooldownBlockedListingId, buyerUuid, now.minusSeconds(30));
        insertTransaction(provider, shopId, cooldownBlockedListingId, buyerUuid, now.minusSeconds(5));
        insertTransaction(provider, shopId, cooldownBlockedListingId, otherBuyerUuid, now.minusSeconds(5));

        insertTransaction(provider, shopId, cooldownAvailableListingId, buyerUuid, now.minusSeconds(10));
        insertTransaction(provider, shopId, cooldownAvailableListingId, buyerUuid, now.minusSeconds(120));

        insertTransaction(provider, shopId, lifetimeBlockedListingId, buyerUuid, now.minusSeconds(3600));
        insertTransaction(provider, shopId, lifetimeBlockedListingId, buyerUuid, now.minusSeconds(1800));

        insertTransaction(provider, otherShopId, otherShopListingId, buyerUuid, now.minusSeconds(1));

        List<ListingWithAccess> entries = listingRepository.findDisplayEntriesByShopId(shopId, buyerUuid, now);

        assertEquals(List.of(
                unrestrictedListingId,
                cooldownBlockedListingId,
                cooldownAvailableListingId,
                lifetimeBlockedListingId
        ), entries.stream().map(entry -> entry.listing().listingId()).toList());

        assertEquals(TradeAccessBlockReason.NONE, entries.get(0).access().blockedReason());
        assertEquals(Integer.MAX_VALUE, entries.get(0).access().remainingLifetimeTrades());

        assertEquals(TradeAccessBlockReason.COOLDOWN_ACTIVE, entries.get(1).access().blockedReason());
        assertEquals(30, entries.get(1).access().remainingCooldownSeconds());
        assertEquals(0, entries.get(1).access().remainingLifetimeTrades());

        assertEquals(TradeAccessBlockReason.NONE, entries.get(2).access().blockedReason());
        assertEquals(0, entries.get(2).access().remainingCooldownSeconds());
        assertEquals(2, entries.get(2).access().remainingLifetimeTrades());

        assertEquals(TradeAccessBlockReason.LIFETIME_LIMIT_REACHED, entries.get(3).access().blockedReason());
        assertEquals(0, entries.get(3).access().remainingCooldownSeconds());
        assertEquals(0, entries.get(3).access().remainingLifetimeTrades());
    }

    private void insertTransaction(ConnectionProvider provider, int shopId, int listingId, UUID buyerUuid, Instant createdAt)
            throws SQLException {
        String sql = "INSERT INTO transactions (shop_id, listing_id, direction, buyer_uuid, seller_uuid, qty, gross, fee, net, created_at) "
                + "VALUES (?, ?, 'PURCHASE', ?, NULL, 1, 10.0, 0.0, 10.0, ?)";
        try (Connection conn = provider.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shopId);
            ps.setInt(2, listingId);
            ps.setString(3, buyerUuid.toString());
            ps.setString(4, SQLITE_TIMESTAMP.format(createdAt));
            ps.executeUpdate();
        }
    }
}

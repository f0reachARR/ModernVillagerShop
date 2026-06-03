package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.storage.mysql.MysqlCoOwnerRepository;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import me.f0reach.vshop.storage.sqlite.SqliteCoOwnerRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class CoOwnerRepositoryContract extends AbstractRepositoryContract {

    private CoOwnerRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteCoOwnerRepository(dataSource())
                : new MysqlCoOwnerRepository(dataSource());
    }

    private CoOwner co(UUID shopId, UUID playerUuid, CoOwnerRole role, String share, UUID addedBy) {
        return new CoOwner(shopId, playerUuid, role, new BigDecimal(share),
                Instant.ofEpochSecond(1_700_000_000L), addedBy);
    }

    @Test
    void upsertsRoundTripPrimary() throws SQLException {
        CoOwnerRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        repo.upsert(co(shop, player, CoOwnerRole.PRIMARY, "100.00", null));

        List<CoOwner> rows = repo.findByShop(shop);
        assertEquals(1, rows.size());
        CoOwner got = rows.get(0);
        assertEquals(CoOwnerRole.PRIMARY, got.role());
        assertEquals(0, got.share().compareTo(new BigDecimal("100.00")));
        assertNull(got.addedBy());
    }

    @Test
    void upsertReplacesRoleAndShare() throws SQLException {
        CoOwnerRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        repo.upsert(co(shop, player, CoOwnerRole.MANAGER, "40.00", null));
        repo.upsert(co(shop, player, CoOwnerRole.STAFF, "0.00", UUID.randomUUID()));

        CoOwner got = repo.findByShop(shop).get(0);
        assertEquals(CoOwnerRole.STAFF, got.role());
        assertEquals(0, got.share().compareTo(new BigDecimal("0.00")));
    }

    @Test
    void findShopsByPlayerFiltersByRole() throws SQLException {
        CoOwnerRepository repo = repository();
        UUID player = UUID.randomUUID();
        UUID shopA = UUID.randomUUID();
        UUID shopB = UUID.randomUUID();
        repo.upsert(co(shopA, player, CoOwnerRole.PRIMARY, "100.00", null));
        repo.upsert(co(shopB, player, CoOwnerRole.MANAGER, "20.00", null));

        assertEquals(2, repo.findShopsByPlayer(player, null).size());
        List<UUID> primaries = repo.findShopsByPlayer(player, CoOwnerRole.PRIMARY);
        assertEquals(1, primaries.size());
        assertEquals(shopA, primaries.get(0));
    }

    @Test
    void deleteRemovesOneRow() throws SQLException {
        CoOwnerRepository repo = repository();
        UUID shop = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        repo.upsert(co(shop, p1, CoOwnerRole.PRIMARY, "60.00", null));
        repo.upsert(co(shop, p2, CoOwnerRole.MANAGER, "40.00", null));

        repo.delete(shop, p1);

        List<CoOwner> rows = repo.findByShop(shop);
        assertEquals(1, rows.size());
        assertEquals(p2, rows.get(0).playerUuid());
    }

    @Test
    void findByShopOnEmptyReturnsEmpty() throws SQLException {
        assertTrue(repository().findByShop(UUID.randomUUID()).isEmpty());
    }
}

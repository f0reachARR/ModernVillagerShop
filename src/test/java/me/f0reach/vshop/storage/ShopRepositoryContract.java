package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.storage.mysql.MysqlShopRepository;
import me.f0reach.vshop.storage.repo.ShopRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Backend-agnostic checks for {@link ShopRepository}. The two concrete
 * subclasses bind it to SQLite and MySQL; everything else is shared so the
 * dialects can't diverge silently (e.g. a column added to one but not the
 * other would surface as a load failure on the dialect that forgot it).
 */
public abstract class ShopRepositoryContract extends AbstractRepositoryContract {

    private ShopRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopRepository(dataSource())
                : new MysqlShopRepository(dataSource());
    }

    private Shop newPlayerShop(UUID id, UUID owner, UUID world, Instant now) {
        return new Shop(id, ShopType.PLAYER, owner,
                new ShopLocation(world, 10.5, 64, -3.25, 90f, 0f),
                UUID.randomUUID(), null, "MyShop", false, 2, now, now);
    }

    private Shop newAdminShop(UUID id, UUID world, Instant now) {
        return new Shop(id, ShopType.ADMIN, null,
                new ShopLocation(world, 0, 0, 0, 0, 0),
                null, null, "Admin", false, 1, now, now);
    }

    @Test
    void roundTripsInsertedShop() throws SQLException {
        ShopRepository repo = repository();
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        repo.insert(newPlayerShop(id, owner, world, now));

        Shop got = repo.findById(id).orElseThrow();
        assertEquals("MyShop", got.name());
        assertEquals(ShopType.PLAYER, got.type());
        assertEquals(owner, got.ownerUuid());
        assertEquals(2, got.rowCount());
        assertFalse(got.suspended());
        assertEquals(10.5, got.location().x());
        assertEquals(64.0, got.location().y());
        assertEquals(-3.25, got.location().z());
        // yaw/pitch round-trip with finite precision on MySQL DECIMAL(10,4).
        assertEquals(90f, got.location().yaw(), 0.001f);
        assertEquals(0f, got.location().pitch(), 0.001f);
        assertEquals(now.toEpochMilli(), got.createdAt().toEpochMilli());
    }

    @Test
    void updatePersistsMutation() throws SQLException {
        ShopRepository repo = repository();
        UUID id = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        Shop shop = newAdminShop(id, world, now);
        repo.insert(shop);

        shop.setSuspended(true);
        shop.setName("Renamed");
        shop.setRowCount(3);
        shop.setUpdatedAt(now.plusSeconds(10));
        repo.update(shop);

        Shop reloaded = repo.findById(id).orElseThrow();
        assertTrue(reloaded.suspended());
        assertEquals("Renamed", reloaded.name());
        assertEquals(3, reloaded.rowCount());
        assertEquals(now.plusSeconds(10).toEpochMilli(), reloaded.updatedAt().toEpochMilli());
    }

    @Test
    void deleteRemovesShop() throws SQLException {
        ShopRepository repo = repository();
        UUID id = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Shop shop = newAdminShop(id, world, Instant.now());
        repo.insert(shop);

        repo.delete(id);

        assertTrue(repo.findById(id).isEmpty());
    }

    @Test
    void adminShopPersistsNullOwner() throws SQLException {
        ShopRepository repo = repository();
        UUID id = UUID.randomUUID();
        repo.insert(newAdminShop(id, UUID.randomUUID(), Instant.now()));

        Shop got = repo.findById(id).orElseThrow();
        assertEquals(ShopType.ADMIN, got.type());
        assertNull(got.ownerUuid());
        assertNull(got.villagerEntityId());
    }

    @Test
    void findAllReturnsEveryShop() throws SQLException {
        ShopRepository repo = repository();
        UUID world = UUID.randomUUID();
        repo.insert(newAdminShop(UUID.randomUUID(), world, Instant.now()));
        repo.insert(newPlayerShop(UUID.randomUUID(), UUID.randomUUID(), world, Instant.now()));

        List<Shop> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findByOwnerOnlyMatches() throws SQLException {
        ShopRepository repo = repository();
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        repo.insert(newPlayerShop(UUID.randomUUID(), owner, world, Instant.now()));
        repo.insert(newPlayerShop(UUID.randomUUID(), UUID.randomUUID(), world, Instant.now()));

        assertEquals(1, repo.findByOwner(owner).size());
        assertEquals(0, repo.findByOwner(UUID.randomUUID()).size());
    }

    @Test
    void findInWorldOnlyMatches() throws SQLException {
        ShopRepository repo = repository();
        UUID world = UUID.randomUUID();
        repo.insert(newPlayerShop(UUID.randomUUID(), UUID.randomUUID(), world, Instant.now()));
        repo.insert(newPlayerShop(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        assertEquals(1, repo.findInWorld(world).size());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() throws SQLException {
        assertTrue(repository().findById(UUID.randomUUID()).isEmpty());
    }
}

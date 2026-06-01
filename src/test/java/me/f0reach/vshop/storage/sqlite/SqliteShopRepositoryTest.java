package me.f0reach.vshop.storage.sqlite;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteShopRepositoryTest {

    private HikariDataSource ds;
    private File dbFile;
    private SqliteShopRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("vshop-test", ".db").toFile();
        HikariConfig hk = new HikariConfig();
        hk.setDriverClassName("org.sqlite.JDBC");
        hk.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hk.setMaximumPoolSize(1);
        ds = new HikariDataSource(hk);
        new SqliteSchemaInitializer(ds).init();
        repo = new SqliteShopRepository(ds);
    }

    @AfterEach
    void tearDown() {
        if (ds != null) ds.close();
        if (dbFile != null) dbFile.delete();
    }

    @Test
    void insertsAndReadsShopRoundTrip() throws SQLException {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Instant now = Instant.now();
        Shop shop = new Shop(id, ShopType.PLAYER, owner,
                new ShopLocation(world, 10.5, 64, -3.25, 90f, 0f),
                UUID.randomUUID(), null, "MyShop", false, 2, now, now);

        repo.insert(shop);

        Optional<Shop> loaded = repo.findById(id);
        assertTrue(loaded.isPresent());
        Shop got = loaded.get();
        assertEquals("MyShop", got.name());
        assertEquals(ShopType.PLAYER, got.type());
        assertEquals(owner, got.ownerUuid());
        assertEquals(2, got.rowCount());
        assertFalse(got.suspended());
        assertEquals(10.5, got.location().x());
    }

    @Test
    void updateChangesPersistedShop() throws SQLException {
        UUID id = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Instant now = Instant.now();
        Shop shop = new Shop(id, ShopType.ADMIN, null,
                new ShopLocation(world, 0, 0, 0, 0, 0),
                null, null, "Admin", false, 1, now, now);
        repo.insert(shop);

        shop.setSuspended(true);
        shop.setName("Renamed");
        shop.setUpdatedAt(now.plusSeconds(10));
        repo.update(shop);

        Shop reloaded = repo.findById(id).orElseThrow();
        assertTrue(reloaded.suspended());
        assertEquals("Renamed", reloaded.name());
    }

    @Test
    void deleteRemovesShop() throws SQLException {
        UUID id = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        Instant now = Instant.now();
        Shop shop = new Shop(id, ShopType.ADMIN, null,
                new ShopLocation(world, 1, 2, 3, 0, 0),
                null, null, "Doomed", false, 1, now, now);
        repo.insert(shop);

        repo.delete(id);

        assertTrue(repo.findById(id).isEmpty());
    }
}

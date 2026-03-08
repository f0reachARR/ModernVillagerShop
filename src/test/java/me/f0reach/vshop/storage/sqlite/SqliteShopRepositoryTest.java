package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteShopRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsFindsAndSoftDeletesShops() throws Exception {
        SqliteTestDatabase database = SqliteTestDatabase.create(tempDir, "shops.db");
        SqliteShopRepository repository = new SqliteShopRepository(database.connectionProvider());

        UUID playerVillagerUuid = UUID.randomUUID();
        UUID playerOwnerUuid = UUID.randomUUID();
        UUID adminVillagerUuid = UUID.randomUUID();

        int playerShopId = repository.create(
                ShopType.PLAYER, playerVillagerUuid, playerOwnerUuid, "world", 1.5, 64.0, -20.25);
        int adminShopId = repository.create(
                ShopType.ADMIN, adminVillagerUuid, UUID.randomUUID(), "world_nether", 8.0, 70.0, 3.0);

        Shop playerShop = repository.findById(playerShopId).orElseThrow();
        assertEquals(ShopType.PLAYER, playerShop.type());
        assertEquals(playerOwnerUuid, playerShop.ownerUuid());
        assertTrue(playerShop.active());
        assertNotNull(playerShop.createdAt());
        assertNotNull(playerShop.updatedAt());

        Shop adminShop = repository.findByVillagerUuid(adminVillagerUuid).orElseThrow();
        assertEquals(adminShopId, adminShop.shopId());
        assertEquals(ShopType.ADMIN, adminShop.type());
        assertNull(adminShop.ownerUuid());

        List<Integer> activeShopIds = repository.findAll().stream().map(Shop::shopId).sorted().toList();
        assertEquals(List.of(playerShopId, adminShopId).stream().sorted().toList(), activeShopIds);

        repository.delete(playerShopId);

        Shop deletedShop = repository.findById(playerShopId).orElseThrow();
        assertFalse(deletedShop.active());
        assertTrue(repository.findAll().stream().map(Shop::shopId).noneMatch(id -> id == playerShopId));
        assertTrue(repository.findByVillagerUuid(playerVillagerUuid).isPresent());
    }
}

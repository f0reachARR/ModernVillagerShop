package me.f0reach.vshop.storage.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteShopInventoryRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void upsertsFindsAndDeletesInventorySlots() throws Exception {
        SqliteTestDatabase database = SqliteTestDatabase.create(tempDir, "inventory.db");
        SqliteShopInventoryRepository repository = new SqliteShopInventoryRepository(database.connectionProvider());

        repository.upsertSlot(7, 4, new byte[] {1, 2, 3});
        repository.upsertSlot(7, 1, new byte[] {9});
        repository.upsertSlot(7, 4, new byte[] {5, 6});
        repository.upsertSlot(8, 0, new byte[] {7});

        Map<Integer, byte[]> shopSevenSlots = repository.findByShopId(7);
        assertEquals(2, shopSevenSlots.size());
        assertArrayEquals(new byte[] {9}, shopSevenSlots.get(1));
        assertArrayEquals(new byte[] {5, 6}, shopSevenSlots.get(4));

        repository.deleteSlot(7, 1);

        Map<Integer, byte[]> remainingSlots = repository.findByShopId(7);
        assertEquals(1, remainingSlots.size());
        assertTrue(remainingSlots.containsKey(4));
        assertArrayEquals(new byte[] {5, 6}, remainingSlots.get(4));
        assertEquals(1, repository.findByShopId(8).size());
    }
}

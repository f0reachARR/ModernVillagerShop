package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.mysql.MysqlShopSlotRepository;
import me.f0reach.vshop.storage.repo.ShopSlotRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopSlotRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ShopSlotRepositoryContract extends AbstractRepositoryContract {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    private ShopSlotRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopSlotRepository(dataSource())
                : new MysqlShopSlotRepository(dataSource());
    }

    private ItemStack diamond() {
        return BukkitTestSupport.item(Material.DIAMOND, 1);
    }

    private ShopSlot sellSlot(UUID shop, int slotIndex) {
        return new ShopSlot(UUID.randomUUID(), shop, slotIndex, TradeSide.SELL, diamond(),
                new BigDecimal("10.0000"), null, 1, 0, null,
                LimitScope.PER_PLAYER, null);
    }

    @Test
    void upsertRoundTrips() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot slot = new ShopSlot(UUID.randomUUID(), shop, 3, TradeSide.BOTH, diamond(),
                new BigDecimal("12.5000"), new BigDecimal("9.0000"),
                4, 64, 8, LimitScope.GLOBAL, Duration.ofHours(2));
        repo.upsert(slot);

        ShopSlot loaded = repo.findById(slot.id()).orElseThrow();
        assertEquals(shop, loaded.shopId());
        assertEquals(3, loaded.slotIndex());
        assertEquals(TradeSide.BOTH, loaded.side());
        assertEquals(0, loaded.unitPrice().compareTo(new BigDecimal("12.5000")));
        assertEquals(0, loaded.buyUnitPrice().compareTo(new BigDecimal("9.0000")));
        assertEquals(4, loaded.unitAmount());
        assertEquals(64, loaded.buyCapacity());
        assertEquals(8, loaded.tradeLimit());
        assertEquals(LimitScope.GLOBAL, loaded.limitScope());
        assertNotNull(loaded.resetPeriod());
        assertEquals(Duration.ofHours(2).getSeconds(), loaded.resetPeriod().getSeconds());
    }

    @Test
    void nullBuyPriceAndLimitsRoundTripAsNull() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot slot = sellSlot(shop, 0);
        repo.upsert(slot);

        ShopSlot loaded = repo.findById(slot.id()).orElseThrow();
        assertNull(loaded.buyUnitPrice());
        assertNull(loaded.tradeLimit());
        assertNull(loaded.resetPeriod());
    }

    @Test
    void upsertReplacesExistingByPrimaryKey() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot slot = sellSlot(shop, 0);
        repo.upsert(slot);

        slot.setUnitPrice(new BigDecimal("99.0000"));
        slot.setUnitAmount(7);
        repo.upsert(slot);

        ShopSlot loaded = repo.findById(slot.id()).orElseThrow();
        assertEquals(0, loaded.unitPrice().compareTo(new BigDecimal("99.0000")));
        assertEquals(7, loaded.unitAmount());
    }

    @Test
    void findByShopOrdersBySlotIndex() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot s2 = sellSlot(shop, 2);
        ShopSlot s0 = sellSlot(shop, 0);
        ShopSlot s1 = sellSlot(shop, 1);
        repo.upsert(s2);
        repo.upsert(s0);
        repo.upsert(s1);

        List<ShopSlot> ordered = repo.findByShop(shop);
        assertEquals(3, ordered.size());
        assertEquals(0, ordered.get(0).slotIndex());
        assertEquals(1, ordered.get(1).slotIndex());
        assertEquals(2, ordered.get(2).slotIndex());
    }

    @Test
    void commandFieldRoundTrips() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot withCommand = new ShopSlot(UUID.randomUUID(), shop, 4, TradeSide.SELL, diamond(),
                new BigDecimal("50.0000"), null, 1, 0, null,
                LimitScope.PER_PLAYER, null, "give <player> diamond <amount>");
        repo.upsert(withCommand);
        ShopSlot loaded = repo.findById(withCommand.id()).orElseThrow();
        assertEquals("give <player> diamond <amount>", loaded.command());
        assertTrue(loaded.hasCommand());

        // Clear back to null and confirm the column is nulled, not left stale.
        loaded.setCommand(null);
        repo.upsert(loaded);
        ShopSlot reloaded = repo.findById(withCommand.id()).orElseThrow();
        assertNull(reloaded.command());
    }

    @Test
    void commandDefaultsToNullForLegacySlots() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot slot = sellSlot(shop, 0);
        repo.upsert(slot);
        assertNull(repo.findById(slot.id()).orElseThrow().command());
    }

    @Test
    void deleteRemovesSlot() throws SQLException {
        ShopSlotRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopSlot slot = sellSlot(shop, 0);
        repo.upsert(slot);

        repo.delete(slot.id());

        assertTrue(repo.findById(slot.id()).isEmpty());
        assertTrue(repo.findByShop(shop).isEmpty());
    }
}

package me.f0reach.vshop.shop.admin;

import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.repo.ShopInventoryRepository;
import me.f0reach.vshop.storage.repo.ShopSlotRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopInventoryRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopSlotRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminShopSlotIOTest extends AbstractRepositoryContract {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    @Override
    protected Backend backend() {
        return Backend.SQLITE;
    }

    @TempDir
    File dataFolder;

    private ShopSlotRepository slotRepo;
    private ShopInventoryRepository inventoryRepo;
    private AdminShopSlotIO io;

    @BeforeEach
    void wireRepos() {
        slotRepo = new SqliteShopSlotRepository(dataSource());
        inventoryRepo = new SqliteShopInventoryRepository(dataSource());
        io = new AdminShopSlotIO(dataFolder, slotRepo, inventoryRepo,
                Clock.fixed(Instant.parse("2026-07-08T12:34:56Z"), ZoneOffset.UTC));
    }

    private Shop adminShop() {
        return new Shop(UUID.randomUUID(), ShopType.ADMIN, null,
                new ShopLocation(UUID.randomUUID(), 0, 64, 0, 0f, 0f),
                UUID.randomUUID(), null, "AdminMarket", false, -1,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private Shop playerShop() {
        return new Shop(UUID.randomUUID(), ShopType.PLAYER, UUID.randomUUID(),
                new ShopLocation(UUID.randomUUID(), 0, 64, 0, 0f, 0f),
                UUID.randomUUID(), null, "PlayerShop", false, 3,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    private ShopSlot sellSlot(UUID shopId, int slotIndex, ItemStack item) {
        return new ShopSlot(UUID.randomUUID(), shopId, slotIndex, TradeSide.SELL, item,
                new BigDecimal("10.0000"), null, 1, 0, null, LimitScope.PER_PLAYER, null);
    }

    @Test
    void roundTripsSlotsBetweenAdminShops() throws Exception {
        Shop src = adminShop();
        Shop dst = adminShop();

        ShopSlot a = sellSlot(src.id(), 0, BukkitTestSupport.item(Material.DIAMOND, 1));
        ShopSlot b = new ShopSlot(UUID.randomUUID(), src.id(), 5, TradeSide.BOTH,
                BukkitTestSupport.item(Material.IRON_INGOT, 1),
                new BigDecimal("3.5000"), new BigDecimal("2.0000"),
                4, 64, 8, LimitScope.GLOBAL, Duration.ofHours(2));
        slotRepo.upsert(a);
        slotRepo.upsert(b);

        io.exportSlots(src, "sample", false);
        AdminShopSlotIO.ImportResult result = io.importSlots(dst, "sample");

        List<ShopSlot> loaded = slotRepo.findByShop(dst.id());
        assertEquals(2, loaded.size());
        assertEquals(0, result.deleted());
        assertEquals(2, result.inserted());

        ShopSlot s0 = loaded.stream().filter(s -> s.slotIndex() == 0).findFirst().orElseThrow();
        assertEquals(TradeSide.SELL, s0.side());
        assertEquals(Material.DIAMOND, s0.itemTemplate().getType());
        assertEquals(0, s0.unitPrice().compareTo(new BigDecimal("10.0000")));
        assertNull(s0.buyUnitPrice());
        assertNull(s0.tradeLimit());
        assertNull(s0.resetPeriod());

        ShopSlot s5 = loaded.stream().filter(s -> s.slotIndex() == 5).findFirst().orElseThrow();
        assertEquals(TradeSide.BOTH, s5.side());
        assertEquals(Material.IRON_INGOT, s5.itemTemplate().getType());
        assertEquals(0, s5.unitPrice().compareTo(new BigDecimal("3.5000")));
        assertEquals(0, s5.buyUnitPrice().compareTo(new BigDecimal("2.0000")));
        assertEquals(4, s5.unitAmount());
        assertEquals(64, s5.buyCapacity());
        assertEquals(8, s5.tradeLimit());
        assertEquals(LimitScope.GLOBAL, s5.limitScope());
        assertNotNull(s5.resetPeriod());
        assertEquals(Duration.ofHours(2).getSeconds(), s5.resetPeriod().getSeconds());
    }

    @Test
    void preservesEnchantsAndCustomNameAcrossRoundTrip() throws Exception {
        Shop src = adminShop();
        Shop dst = adminShop();

        ItemStack sword = BukkitTestSupport.item(Material.DIAMOND_SWORD, 1);
        ItemMeta meta = sword.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        sword.setItemMeta(meta);

        slotRepo.upsert(sellSlot(src.id(), 0, sword));

        io.exportSlots(src, "fidelity", false);
        io.importSlots(dst, "fidelity");

        List<ShopSlot> loaded = slotRepo.findByShop(dst.id());
        assertEquals(1, loaded.size());
        ItemStack roundTripped = loaded.get(0).itemTemplate();
        assertEquals(Material.DIAMOND_SWORD, roundTripped.getType());
        assertEquals(5, roundTripped.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertNotNull(roundTripped.getItemMeta().displayName());
    }

    @Test
    void importReplacesAllExistingSlotsAndWritesBackup() throws Exception {
        Shop src = adminShop();
        Shop dst = adminShop();

        // Src exports 1 slot; dst already has 3 slots.
        slotRepo.upsert(sellSlot(src.id(), 0, BukkitTestSupport.item(Material.APPLE, 1)));
        slotRepo.upsert(sellSlot(dst.id(), 0, BukkitTestSupport.item(Material.STONE, 1)));
        slotRepo.upsert(sellSlot(dst.id(), 1, BukkitTestSupport.item(Material.DIRT, 1)));
        slotRepo.upsert(sellSlot(dst.id(), 2, BukkitTestSupport.item(Material.SAND, 1)));

        io.exportSlots(src, "overwrite", false);
        AdminShopSlotIO.ImportResult result = io.importSlots(dst, "overwrite");

        assertEquals(3, result.deleted());
        assertEquals(1, result.inserted());
        List<ShopSlot> loaded = slotRepo.findByShop(dst.id());
        assertEquals(1, loaded.size());
        assertEquals(Material.APPLE, loaded.get(0).itemTemplate().getType());

        assertNotNull(result.backupPath());
        assertTrue(Files.exists(result.backupPath()));
        YamlConfiguration backup = YamlConfiguration.loadConfiguration(result.backupPath().toFile());
        assertEquals(3, backup.getConfigurationSection("slots").getKeys(false).size());
    }

    @Test
    void exportRefusesToOverwriteExistingFileByDefault() throws Exception {
        Shop src = adminShop();
        slotRepo.upsert(sellSlot(src.id(), 0, BukkitTestSupport.item(Material.APPLE, 1)));

        io.exportSlots(src, "dup", false);
        assertThrows(FileAlreadyExistsException.class, () -> io.exportSlots(src, "dup", false));
        // overwrite=true succeeds.
        io.exportSlots(src, "dup", true);
    }

    @Test
    void importRejectsMissingFile() {
        Shop dst = adminShop();
        assertThrows(NoSuchFileException.class, () -> io.importSlots(dst, "no-such"));
    }

    @Test
    void rejectsFileNameWithPathTraversal() {
        Shop src = adminShop();
        assertThrows(IllegalArgumentException.class, () -> io.exportSlots(src, "../etc/passwd", false));
        assertThrows(IllegalArgumentException.class, () -> io.exportSlots(src, "foo/bar", false));
        assertThrows(IllegalArgumentException.class, () -> io.exportSlots(src, "foo\\bar", false));
    }

    @Test
    void refusesPlayerShop() throws Exception {
        Shop player = playerShop();
        slotRepo.upsert(sellSlot(player.id(), 0, BukkitTestSupport.item(Material.STONE, 1)));
        assertThrows(IllegalArgumentException.class, () -> io.exportSlots(player, "player", false));
        assertThrows(IllegalArgumentException.class, () -> io.importSlots(player, "player"));
    }

    @Test
    void rejectsUnknownVersion() throws Exception {
        Shop dst = adminShop();
        Path target = io.resolveExportPath("bad");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("version", 99);
        yml.createSection("slots"); // present but wrong version
        yml.save(target.toFile());
        assertThrows(AdminShopSlotIO.InvalidExportFormatException.class,
                () -> io.importSlots(dst, "bad"));
    }

    @Test
    void rejectsDuplicateSlotIndex() throws Exception {
        Shop dst = adminShop();
        Path target = io.resolveExportPath("dup-index");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("version", AdminShopSlotIO.FORMAT_VERSION);
        var s0 = yml.createSection("slots.entry-0");
        s0.set("slot_index", 3);
        s0.set("side", "SELL");
        s0.set("item", BukkitTestSupport.item(Material.APPLE, 1));
        s0.set("unit_price", "1");
        var s1 = yml.createSection("slots.entry-1");
        s1.set("slot_index", 3);
        s1.set("side", "SELL");
        s1.set("item", BukkitTestSupport.item(Material.STONE, 1));
        s1.set("unit_price", "1");
        yml.save(target.toFile());
        assertThrows(AdminShopSlotIO.InvalidExportFormatException.class,
                () -> io.importSlots(dst, "dup-index"));
    }
}

package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.model.ShopEntityKind;
import me.f0reach.vshop.model.SkinVariant;
import me.f0reach.vshop.storage.mysql.MysqlShopAppearanceRepository;
import me.f0reach.vshop.storage.repo.ShopAppearanceRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopAppearanceRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class ShopAppearanceRepositoryContract extends AbstractRepositoryContract {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureStarted();
    }

    private ShopAppearanceRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqliteShopAppearanceRepository(dataSource())
                : new MysqlShopAppearanceRepository(dataSource());
    }

    private static ShopAppearance playerNpc(UUID shopId) {
        ShopAppearance a = new ShopAppearance(shopId, ShopEntityKind.FANCY_NPC);
        a.setEntityType(EntityType.PLAYER);
        a.setSkin("Notch");
        a.setSkinVariant(SkinVariant.SLIM);
        a.setGlowing(true);
        a.setGlowColor(NamedTextColor.AQUA);
        a.setScale(1.5f);
        a.setTurnToPlayer(true);
        a.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        return a;
    }

    @Test
    void missingShopHasNoAppearance() throws SQLException {
        assertTrue(repository().find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void roundTripsEveryField() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopAppearance written = playerNpc(shop);
        repo.upsert(written);

        ShopAppearance got = repo.find(shop).orElseThrow();
        assertEquals(ShopEntityKind.FANCY_NPC, got.backend());
        assertEquals(EntityType.PLAYER, got.entityType());
        assertEquals("Notch", got.skin());
        assertEquals(SkinVariant.SLIM, got.skinVariant());
        assertTrue(got.glowing());
        assertSame(NamedTextColor.AQUA, got.glowColor());
        assertEquals(1.5f, got.scale());
        assertEquals(Boolean.TRUE, got.turnToPlayer());
        assertEquals(written.updatedAt(), got.updatedAt());
    }

    @Test
    void unsetOptionalFieldsStayNull() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        repo.upsert(new ShopAppearance(shop, ShopEntityKind.VILLAGER));

        ShopAppearance got = repo.find(shop).orElseThrow();
        assertEquals(ShopEntityKind.VILLAGER, got.backend());
        assertNull(got.entityType());
        assertNull(got.skin());
        assertNull(got.skinVariant());
        assertNull(got.glowColor());
        // Nullable-boolean and nullable-float must survive as null, not 0/false.
        assertNull(got.scale());
        assertNull(got.turnToPlayer());
        assertTrue(got.attributes().isEmpty());
        assertTrue(got.equipment().isEmpty());
    }

    @Test
    void upsertReplacesExistingRow() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        repo.upsert(playerNpc(shop));

        ShopAppearance second = new ShopAppearance(shop, ShopEntityKind.VILLAGER);
        second.setUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        repo.upsert(second);

        ShopAppearance got = repo.find(shop).orElseThrow();
        assertEquals(ShopEntityKind.VILLAGER, got.backend());
        assertNull(got.skin());
        assertNull(got.scale());
    }

    @Test
    void roundTripsEquipment() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopAppearance a = playerNpc(shop);
        a.equipment().put("MAINHAND", BukkitTestSupport.item(Material.DIAMOND_SWORD));
        a.equipment().put("HEAD", BukkitTestSupport.item(Material.DIAMOND_HELMET));
        repo.upsert(a);

        ShopAppearance got = repo.find(shop).orElseThrow();
        assertEquals(2, got.equipment().size());
        assertEquals(Material.DIAMOND_SWORD, got.equipment().get("MAINHAND").getType());
        assertEquals(Material.DIAMOND_HELMET, got.equipment().get("HEAD").getType());
    }

    @Test
    void upsertReplacesTheWholeEquipmentSet() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopAppearance a = playerNpc(shop);
        a.equipment().put("MAINHAND", BukkitTestSupport.item(Material.DIAMOND_SWORD));
        a.equipment().put("HEAD", BukkitTestSupport.item(Material.DIAMOND_HELMET));
        repo.upsert(a);

        // Clearing a slot must actually remove it, not leave the old row behind.
        ShopAppearance b = playerNpc(shop);
        b.equipment().put("HEAD", BukkitTestSupport.item(Material.GOLDEN_HELMET));
        repo.upsert(b);

        ShopAppearance got = repo.find(shop).orElseThrow();
        assertEquals(1, got.equipment().size());
        assertEquals(Material.GOLDEN_HELMET, got.equipment().get("HEAD").getType());
    }

    @Test
    void roundTripsAttributes() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopAppearance a = playerNpc(shop);
        a.attributes().put("pose", "sitting");
        a.attributes().put("variant", "warm");
        repo.upsert(a);

        ShopAppearance got = repo.find(shop).orElseThrow();
        assertEquals(2, got.attributes().size());
        assertEquals("sitting", got.attributes().get("pose"));
        assertEquals("warm", got.attributes().get("variant"));
    }

    @Test
    void deleteRemovesAppearanceAndEquipment() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID shop = UUID.randomUUID();
        ShopAppearance a = playerNpc(shop);
        a.equipment().put("MAINHAND", BukkitTestSupport.item(Material.DIAMOND_SWORD));
        repo.upsert(a);

        repo.delete(shop);
        assertTrue(repo.find(shop).isEmpty());

        // Re-inserting must not resurrect the old equipment row.
        repo.upsert(new ShopAppearance(shop, ShopEntityKind.VILLAGER));
        assertTrue(repo.find(shop).orElseThrow().equipment().isEmpty());
    }

    @Test
    void findAllReturnsEveryShopWithItsOwnEquipment() throws SQLException {
        ShopAppearanceRepository repo = repository();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        ShopAppearance a = playerNpc(first);
        a.equipment().put("MAINHAND", BukkitTestSupport.item(Material.DIAMOND_SWORD));
        repo.upsert(a);

        ShopAppearance b = playerNpc(second);
        b.equipment().put("HEAD", BukkitTestSupport.item(Material.GOLDEN_HELMET));
        repo.upsert(b);

        List<ShopAppearance> all = repo.findAll();
        assertEquals(2, all.size());
        ShopAppearance loadedFirst = all.stream()
                .filter(x -> x.shopId().equals(first)).findFirst().orElseThrow();
        ShopAppearance loadedSecond = all.stream()
                .filter(x -> x.shopId().equals(second)).findFirst().orElseThrow();
        assertEquals(List.of("MAINHAND"), List.copyOf(loadedFirst.equipment().keySet()));
        assertEquals(List.of("HEAD"), List.copyOf(loadedSecond.equipment().keySet()));
    }

    @Test
    void findAllOnEmptyTableReturnsEmptyList() throws SQLException {
        assertTrue(repository().findAll().isEmpty());
    }
}

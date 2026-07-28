package me.f0reach.vshop.command;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which shops the {@code <shopId>} completion offers. The tooltip rendering
 * needs a live plugin, so only the selection is covered here — that is where
 * the "nearby only" rule lives.
 */
class ShopIdSuggestionsTest {

    private static World world;
    private static World other;

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureBukkit();
        world = MockBukkit.getMock().addSimpleWorld("suggest-test-" + System.nanoTime());
        other = MockBukkit.getMock().addSimpleWorld("suggest-other-" + System.nanoTime());
    }

    private static Shop shopAt(String name, World w, double x, double z) {
        Instant now = Instant.now();
        return new Shop(UUID.randomUUID(), ShopType.PLAYER, UUID.randomUUID(),
                new ShopLocation(w.getUID(), x, 64, z, 0f, 0f),
                null, null, name, false, 3, now, now);
    }

    private static List<String> names(List<ShopIdSuggestions.Candidate> candidates) {
        return candidates.stream().map(c -> c.shop().name()).toList();
    }

    @Test
    void onlyShopsWithinRangeOfThePlayerAreOffered() {
        Shop near = shopAt("near", world, 10, 0);
        Shop far = shopAt("far", world, 400, 0);
        var picked = ShopIdSuggestions.select(List.of(near, far), "",
                new Location(world, 0, 64, 0), 20);
        assertEquals(List.of("near"), names(picked));
    }

    @Test
    void shopsInAnotherWorldAreNeverNear() {
        Shop sameSpotOtherWorld = shopAt("elsewhere", other, 0, 0);
        var picked = ShopIdSuggestions.select(List.of(sameSpotOtherWorld), "",
                new Location(world, 0, 64, 0), 20);
        assertTrue(picked.isEmpty());
    }

    @Test
    void nearestSurviveTheCapAndCarryTheirDistance() {
        Shop a = shopAt("a", world, 30, 0);
        Shop b = shopAt("b", world, 5, 0);
        Shop c = shopAt("c", world, 15, 0);
        var picked = ShopIdSuggestions.select(List.of(a, b, c), "",
                new Location(world, 0, 64, 0), 2);
        assertEquals(List.of("b", "c"), names(picked));
        assertEquals(5.0, picked.get(0).distance(), 1e-9);
    }

    @Test
    void aSenderWithoutAPositionKeepsEveryShopOrderedByName() {
        Shop far = shopAt("zulu", world, 5000, 0);
        Shop elsewhere = shopAt("alpha", other, 0, 0);
        var picked = ShopIdSuggestions.select(List.of(far, elsewhere), "", null, 20);
        assertEquals(List.of("alpha", "zulu"), names(picked));
        assertNull(picked.get(0).distance());
    }

    @Test
    void thePrefixStillFiltersByTheShortId() {
        Shop shop = shopAt("only", world, 1, 0);
        String id = shop.id().toString().substring(0, 8);
        Location origin = new Location(world, 0, 64, 0);
        assertEquals(1, ShopIdSuggestions.select(List.of(shop), id, origin, 20).size());
        assertTrue(ShopIdSuggestions.select(List.of(shop), "zzzzzzzz", origin, 20).isEmpty());
    }
}

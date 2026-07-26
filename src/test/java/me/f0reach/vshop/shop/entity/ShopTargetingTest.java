package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Geometry of the NPC hitbox we raycast against. The intersection itself is
 * Bukkit's; what has to be right here is where the box sits and how it scales.
 */
class ShopTargetingTest {

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureBukkit();
    }

    private static ShopLocation at(double x, double y, double z) {
        return new ShopLocation(UUID.randomUUID(), x, y, z, 0f, 0f);
    }

    @Test
    void hitboxStandsOnTheAnchorAndIsCentredHorizontally() {
        BoundingBox box = ShopTargeting.hitboxOf(at(10, 64, -5), 1.0f);
        assertEquals(10.0, box.getCenterX(), 1e-9);
        assertEquals(-5.0, box.getCenterZ(), 1e-9);
        // Feet on the anchor, not centred on it: the shop's y is ground level.
        assertEquals(64.0, box.getMinY(), 1e-9);
        assertEquals(64.0 + 1.8, box.getMaxY(), 1e-9);
        assertEquals(0.6, box.getWidthX(), 1e-9);
        assertEquals(0.6, box.getWidthZ(), 1e-9);
    }

    @Test
    void scaleGrowsTheBoxAroundTheSameAnchor() {
        BoundingBox box = ShopTargeting.hitboxOf(at(0, 0, 0), 2.0f);
        assertEquals(1.2, box.getWidthX(), 1e-9);
        assertEquals(3.6, box.getHeight(), 1e-9);
        assertEquals(0.0, box.getMinY(), 1e-9);
    }

    @Test
    void missingOrNonsensicalScaleFallsBackToOne() {
        assertEquals(1.0f, ShopTargeting.normaliseScale(null));
        assertEquals(1.0f, ShopTargeting.normaliseScale(0.0f));
        assertEquals(1.0f, ShopTargeting.normaliseScale(-2.0f));
        assertEquals(1.5f, ShopTargeting.normaliseScale(1.5f));
    }

    @Test
    void horizontalLookHitsTheBodyOfAnNpcAhead() {
        BoundingBox box = ShopTargeting.hitboxOf(at(0, 64, 5), 1.0f);
        // Eye height 1.62 above ground, looking straight down +Z.
        Vector eye = new Vector(0, 65.62, 0);
        assertNotNull(box.rayTrace(eye, new Vector(0, 0, 1), 10));
    }

    @Test
    void lookingPastTheNpcMisses() {
        BoundingBox box = ShopTargeting.hitboxOf(at(0, 64, 5), 1.0f);
        Vector eye = new Vector(0, 65.62, 0);
        // A metre to the side of a 0.6-wide box.
        assertNull(box.rayTrace(eye, new Vector(1, 0, 1).normalize(), 10));
        // Correct direction but out of reach.
        assertNull(box.rayTrace(eye, new Vector(0, 0, 1), 2));
    }

    @Test
    void aTallNpcIsHitWhereADefaultOneWouldBeMissed() {
        Vector eye = new Vector(0, 65.62, 0);
        Vector up = new Vector(0, 2, 5).normalize();
        assertNull(ShopTargeting.hitboxOf(at(0, 64, 5), 1.0f).rayTrace(eye, up, 10));
        assertNotNull(ShopTargeting.hitboxOf(at(0, 64, 5), 3.0f).rayTrace(eye, up, 10));
    }
}

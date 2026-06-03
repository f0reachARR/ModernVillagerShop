package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShareDistributionTest {

    private CoOwner co(CoOwnerRole role, String share) {
        return new CoOwner(UUID.randomUUID(), UUID.randomUUID(), role,
                new BigDecimal(share), Instant.now(), null);
    }

    @Test
    void primaryReceivesEverything_whenSoleOwner() {
        var primary = co(CoOwnerRole.PRIMARY, "100.00");
        var out = ShareDistribution.distribute(List.of(primary), new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("100.00"), out.get(primary.playerUuid()));
        assertEquals(1, out.size());
    }

    @Test
    void splitsEvenly_betweenPrimaryAndManager() {
        var primary = co(CoOwnerRole.PRIMARY, "50.00");
        var manager = co(CoOwnerRole.MANAGER, "50.00");
        var out = ShareDistribution.distribute(List.of(primary, manager),
                new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("50.00"), out.get(primary.playerUuid()));
        assertEquals(new BigDecimal("50.00"), out.get(manager.playerUuid()));
    }

    @Test
    void staffReceivesNothing() {
        var primary = co(CoOwnerRole.PRIMARY, "100.00");
        var staff = co(CoOwnerRole.STAFF, "0.00");
        var out = ShareDistribution.distribute(List.of(primary, staff),
                new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("100.00"), out.get(primary.playerUuid()));
        assertEquals(1, out.size());
    }

    @Test
    void remainderGoesToPrimary() {
        // 100 split 33.33% / 33.33% / 33.34% across three owners with 2 fraction digits
        // produces some rounding; remainder should snap to PRIMARY so the sum equals 100.00 exactly.
        var primary = co(CoOwnerRole.PRIMARY, "33.33");
        var m1 = co(CoOwnerRole.MANAGER, "33.33");
        var m2 = co(CoOwnerRole.MANAGER, "33.34");
        Map<UUID, BigDecimal> out = ShareDistribution.distribute(List.of(primary, m1, m2),
                new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : out.values()) sum = sum.add(v);
        assertEquals(0, sum.compareTo(new BigDecimal("100.00")), "sum should match exactly, got " + sum);
        // PRIMARY's share should absorb the rounding remainder when present.
        assertTrue(out.get(primary.playerUuid()).compareTo(new BigDecimal("33.33")) >= 0);
    }

    @Test
    void zeroNetAmountProducesEmptyMap() {
        var primary = co(CoOwnerRole.PRIMARY, "100.00");
        var out = ShareDistribution.distribute(List.of(primary), BigDecimal.ZERO, 2, RoundingMode.HALF_UP);
        assertEquals(0, out.size());
    }

    @Test
    void negativeNetAmountProducesEmptyMap() {
        var primary = co(CoOwnerRole.PRIMARY, "100.00");
        var out = ShareDistribution.distribute(List.of(primary), new BigDecimal("-50.00"),
                2, RoundingMode.HALF_UP);
        assertEquals(0, out.size());
    }

    @Test
    void emptyCoOwnerListProducesEmptyMap() {
        var out = ShareDistribution.distribute(List.of(), new BigDecimal("100.00"),
                2, RoundingMode.HALF_UP);
        assertEquals(0, out.size());
    }

    @Test
    void zeroShareReceiverIsIgnored() {
        // A non-PRIMARY with 0% share is filtered out before allocation.
        var primary = co(CoOwnerRole.PRIMARY, "100.00");
        var manager = co(CoOwnerRole.MANAGER, "0.00");
        var out = ShareDistribution.distribute(List.of(primary, manager),
                new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        assertEquals(1, out.size());
        assertEquals(0, out.get(primary.playerUuid()).compareTo(new BigDecimal("100.00")));
    }

    @Test
    void multipleManagersWithoutPrimaryDoNotReceiveRemainder() {
        // If there's no PRIMARY at all, the remainder is dropped on the floor;
        // the spec puts that money into the void rather than picking arbitrarily.
        var m1 = co(CoOwnerRole.MANAGER, "33.33");
        var m2 = co(CoOwnerRole.MANAGER, "33.33");
        var out = ShareDistribution.distribute(List.of(m1, m2),
                new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : out.values()) sum = sum.add(v);
        // No PRIMARY means we don't carry the leftover; sum is < 100.
        assertTrue(sum.compareTo(new BigDecimal("100.00")) < 0);
    }

    @Test
    void nullShareIsIgnored() {
        var primary = co(CoOwnerRole.PRIMARY, "100.00");
        var manager = new CoOwner(UUID.randomUUID(), UUID.randomUUID(), CoOwnerRole.MANAGER,
                null, java.time.Instant.now(), null);
        var out = ShareDistribution.distribute(List.of(primary, manager),
                new BigDecimal("100.00"), 2, RoundingMode.HALF_UP);
        assertEquals(1, out.size());
        assertEquals(0, out.get(primary.playerUuid()).compareTo(new BigDecimal("100.00")));
    }
}

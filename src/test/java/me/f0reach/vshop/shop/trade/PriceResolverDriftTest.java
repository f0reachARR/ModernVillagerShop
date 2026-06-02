package me.f0reach.vshop.shop.trade;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceResolverDriftTest {

    private static final BigDecimal ONE_PERCENT = new BigDecimal("0.01");

    @Test
    void identicalPrices_neverDrift() {
        assertFalse(PriceResolver.isDriftBeyondTolerance(
                new BigDecimal("100"), new BigDecimal("100"), ONE_PERCENT));
    }

    @Test
    void halfPercentMove_withinOnePercentTolerance() {
        // 100 → 100.5 is 0.5% drift, well under the 1% cap.
        assertFalse(PriceResolver.isDriftBeyondTolerance(
                new BigDecimal("100"), new BigDecimal("100.5"), ONE_PERCENT));
    }

    @Test
    void twoPercentMove_exceedsOnePercentTolerance() {
        assertTrue(PriceResolver.isDriftBeyondTolerance(
                new BigDecimal("100"), new BigDecimal("102.00"), ONE_PERCENT));
    }

    @Test
    void downwardMove_alsoTriggers() {
        assertTrue(PriceResolver.isDriftBeyondTolerance(
                new BigDecimal("100"), new BigDecimal("98.00"), ONE_PERCENT));
    }

    @Test
    void zeroTolerance_treatedAsDisabled() {
        // priceProvider.enabled=false / unset path: any drift is permitted.
        assertFalse(PriceResolver.isDriftBeyondTolerance(
                new BigDecimal("100"), new BigDecimal("999"), BigDecimal.ZERO));
    }

    @Test
    void nullTolerance_treatedAsDisabled() {
        assertFalse(PriceResolver.isDriftBeyondTolerance(
                new BigDecimal("100"), new BigDecimal("999"), null));
    }

    @Test
    void zeroSnapshot_doesNotDivideByZero_butCurrentDriftStillCaught() {
        // 0 → 100 is bigger than the larger absolute (100), so ratio = 1.0.
        assertTrue(PriceResolver.isDriftBeyondTolerance(
                BigDecimal.ZERO, new BigDecimal("100"), ONE_PERCENT));
    }

    @Test
    void bothZero_noDrift() {
        assertFalse(PriceResolver.isDriftBeyondTolerance(
                BigDecimal.ZERO, BigDecimal.ZERO, ONE_PERCENT));
    }

    @Test
    void nullSnapshot_isSafe() {
        assertFalse(PriceResolver.isDriftBeyondTolerance(null, new BigDecimal("100"), ONE_PERCENT));
    }
}

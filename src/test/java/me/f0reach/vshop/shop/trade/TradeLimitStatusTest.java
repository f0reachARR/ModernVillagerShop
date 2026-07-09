package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeLimitUsage;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;
import me.f0reach.vshop.testsupport.BukkitTestSupport;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeLimitStatusTest {

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    @BeforeAll
    static void bootBukkit() {
        BukkitTestSupport.ensureBukkit();
    }

    @Test
    void unlimitedSlot_yieldsNull() {
        ShopSlot slot = slot(null, null);
        assertNull(TradeLimitStatus.of(slot, Optional.empty(), NOW));
    }

    @Test
    void limitedNoReset_noUsage_reportsFullRemaining() {
        ShopSlot slot = slot(64, null);
        TradeLimitStatus s = TradeLimitStatus.of(slot, Optional.empty(), NOW);
        assertNotNull(s);
        assertEquals(64, s.limit());
        assertEquals(0, s.used());
        assertEquals(64, s.remaining());
        assertFalse(s.hasReset());
        assertNull(s.resetAt());
        assertFalse(s.windowActive(NOW));
    }

    @Test
    void limitedWithReset_activeWindow_reportsUsageAndCountdown() {
        Duration period = Duration.ofHours(24);
        ShopSlot slot = slot(64, period);
        Instant windowStart = NOW.minus(Duration.ofHours(3));
        TradeLimitStatus s = TradeLimitStatus.of(slot, Optional.of(usage(slot, 10, windowStart)), NOW);
        assertNotNull(s);
        assertEquals(10, s.used());
        assertEquals(54, s.remaining());
        assertTrue(s.hasReset());
        assertEquals(windowStart.plus(period), s.resetAt());
        assertTrue(s.windowActive(NOW));
        assertEquals(Duration.ofHours(21), s.timeUntilReset(NOW));
    }

    @Test
    void limitedWithReset_windowElapsed_countsAsFresh() {
        Duration period = Duration.ofHours(2);
        ShopSlot slot = slot(64, period);
        // Window opened 3h ago, period is 2h → already elapsed.
        Instant windowStart = NOW.minus(Duration.ofHours(3));
        TradeLimitStatus s = TradeLimitStatus.of(slot, Optional.of(usage(slot, 64, windowStart)), NOW);
        assertNotNull(s);
        assertEquals(0, s.used(), "elapsed window collapses to fresh");
        assertEquals(64, s.remaining());
        assertFalse(s.windowActive(NOW));
        assertEquals(Duration.ZERO, s.timeUntilReset(NOW));
    }

    @Test
    void limitedNoReset_withUsage_neverResets() {
        ShopSlot slot = slot(64, null);
        Instant windowStart = NOW.minus(Duration.ofDays(30));
        TradeLimitStatus s = TradeLimitStatus.of(slot, Optional.of(usage(slot, 40, windowStart)), NOW);
        assertNotNull(s);
        // Even ancient usage sticks when there's no reset period.
        assertEquals(40, s.used());
        assertEquals(24, s.remaining());
        assertFalse(s.hasReset());
        assertNull(s.resetAt());
    }

    @Test
    void scopeKey_perPlayer_returnsPlayerUuid() {
        ShopSlot slot = slot(64, null, LimitScope.PER_PLAYER);
        UUID player = UUID.randomUUID();
        assertEquals(player, TradeLimitStatus.scopeKey(slot, player));
    }

    @Test
    void scopeKey_global_returnsSentinel() {
        ShopSlot slot = slot(64, null, LimitScope.GLOBAL);
        UUID player = UUID.randomUUID();
        assertEquals(ShopLimitRepository.GLOBAL_KEY, TradeLimitStatus.scopeKey(slot, player));
    }

    private static ShopSlot slot(Integer limit, Duration reset) {
        return slot(limit, reset, LimitScope.PER_PLAYER);
    }

    private static ShopSlot slot(Integer limit, Duration reset, LimitScope scope) {
        return new ShopSlot(UUID.randomUUID(), UUID.randomUUID(), 0, TradeSide.SELL,
                BukkitTestSupport.item(Material.STONE), BigDecimal.ONE, BigDecimal.ONE, 1, -1,
                limit, scope, reset);
    }

    private static TradeLimitUsage usage(ShopSlot slot, int amount, Instant windowStart) {
        return new TradeLimitUsage(slot.id(), UUID.randomUUID(), amount, windowStart);
    }
}

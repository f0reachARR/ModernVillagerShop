package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeLimitUsage;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A read-only snapshot of a slot's trade-limit state for a specific scope key
 * (either the viewing player for {@link LimitScope#PER_PLAYER} or
 * {@link ShopLimitRepository#GLOBAL_KEY} for {@link LimitScope#GLOBAL}).
 *
 * <p>Semantics mirror {@code TradeService.checkAndReserveLimit} and
 * {@code TradeFlow.remainingTradeLimit}: if the current window has already
 * elapsed at {@code now}, the returned {@link #used()} is 0 and
 * {@link #remaining()} equals the configured limit — the next trade would
 * anchor a fresh window.</p>
 *
 * <p>{@link #resetAt()} is present only when a reset period is configured
 * <em>and</em> a window has been anchored (i.e. at least one prior trade). If
 * the anchored window has already elapsed at {@code now}, {@link #resetAt()}
 * is still returned but is in the past — callers can distinguish this via
 * {@link #windowActive(Instant)}.</p>
 */
public record TradeLimitStatus(
        int limit,
        int used,
        int remaining,
        Duration resetPeriod,
        Instant windowStart,
        Instant resetAt
) {

    /** True when a reset period is configured on the slot. */
    public boolean hasReset() { return resetPeriod != null; }

    /** True when a window has been anchored and its deadline is still in the future at {@code now}. */
    public boolean windowActive(Instant now) {
        return resetAt != null && now.isBefore(resetAt);
    }

    /** Duration from {@code now} until {@link #resetAt}, or {@link Duration#ZERO} if not applicable. */
    public Duration timeUntilReset(Instant now) {
        if (resetAt == null) return Duration.ZERO;
        Duration d = Duration.between(now, resetAt);
        return d.isNegative() ? Duration.ZERO : d;
    }

    /**
     * Compute the status from the slot config, the stored usage row (if any),
     * and the current instant.
     *
     * <p>Returns {@code null} when the slot has no trade limit configured —
     * callers should treat that as "no limit UI to show".</p>
     */
    public static TradeLimitStatus of(ShopSlot slot, Optional<TradeLimitUsage> usage, Instant now) {
        if (slot.tradeLimit() == null) return null;
        int limit = slot.tradeLimit();
        Duration period = slot.resetPeriod();

        int amount = usage.map(TradeLimitUsage::amount).orElse(0);
        Instant windowStart = usage.map(TradeLimitUsage::windowStart).orElse(null);
        Instant resetAt = (period != null && windowStart != null) ? windowStart.plus(period) : null;

        // Window has already elapsed: next trade would reset to 0. Mirror that
        // here so a stale usage row doesn't confuse the display / pre-check.
        if (resetAt != null && !now.isBefore(resetAt)) {
            amount = 0;
        }

        int remaining = Math.max(0, limit - amount);
        return new TradeLimitStatus(limit, amount, remaining, period, windowStart, resetAt);
    }

    /** Resolves the scope key used to look up {@link TradeLimitUsage} rows. */
    public static UUID scopeKey(ShopSlot slot, UUID playerUuid) {
        return slot.limitScope() == LimitScope.GLOBAL
                ? ShopLimitRepository.GLOBAL_KEY
                : playerUuid;
    }
}

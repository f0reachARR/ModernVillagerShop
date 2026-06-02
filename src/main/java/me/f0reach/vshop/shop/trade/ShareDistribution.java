package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits a SELL payout across co-owners according to their {@code share} (a
 * percent, 0–100). STAFF do not receive revenue; their share contribution is
 * ignored. Any rounding remainder is dropped on PRIMARY so the sum exactly
 * matches the input.
 */
public final class ShareDistribution {

    private ShareDistribution() {}

    public static Map<UUID, BigDecimal> distribute(List<CoOwner> coOwners, BigDecimal netAmount,
                                                   int scale, RoundingMode roundingMode) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        if (netAmount.signum() <= 0) return result;

        List<CoOwner> receivers = new ArrayList<>();
        for (CoOwner co : coOwners) {
            if (co.role() == CoOwnerRole.STAFF) continue;
            if (co.share() == null || co.share().signum() <= 0) continue;
            receivers.add(co);
        }
        if (receivers.isEmpty()) return result;

        UUID primary = null;
        for (CoOwner co : receivers) {
            if (co.role() == CoOwnerRole.PRIMARY) { primary = co.playerUuid(); break; }
        }

        BigDecimal hundred = new BigDecimal("100");
        BigDecimal assigned = BigDecimal.ZERO;
        for (CoOwner co : receivers) {
            BigDecimal portion = netAmount.multiply(co.share())
                    .divide(hundred, scale, roundingMode);
            if (portion.signum() <= 0) continue;
            result.merge(co.playerUuid(), portion, BigDecimal::add);
            assigned = assigned.add(portion);
        }

        BigDecimal remainder = netAmount.subtract(assigned).setScale(scale, roundingMode);
        if (remainder.signum() != 0 && primary != null) {
            result.merge(primary, remainder, BigDecimal::add);
        }
        return result;
    }
}

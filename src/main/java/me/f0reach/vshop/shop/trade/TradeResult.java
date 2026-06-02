package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.TradeSide;

import java.math.BigDecimal;

/**
 * Outcome of a {@link TradeService#execute} call. {@code Success} carries the
 * settlement details needed for the post-trade chat notice; {@code Failure}
 * carries the message key the UI should surface.
 */
public sealed interface TradeResult permits TradeResult.Success, TradeResult.Failure {

    record Success(
            TradeSide side,
            int amount,           // total items
            BigDecimal unitPrice,
            BigDecimal gross,
            BigDecimal fee,
            BigDecimal netToShop, // for SELL: distributed among co-owners; for BUY: paid to deliverer
            long transactionId
    ) implements TradeResult {}

    record Failure(String messageKey) implements TradeResult {}
}

package me.f0reach.vshop.shop.trade;

import me.f0reach.vshop.model.TradeSide;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Outcome of a {@link TradeService#execute} call. {@code Success} carries the
 * settlement details needed for the post-trade chat notice; {@code Failure}
 * carries the message key the UI should surface, plus any MiniMessage
 * placeholders that should be substituted into it.
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

    record Failure(String messageKey, Map<String, String> placeholders) implements TradeResult {
        public Failure(String messageKey) {
            this(messageKey, Map.of());
        }
    }
}

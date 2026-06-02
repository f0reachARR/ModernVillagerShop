package me.f0reach.vshop.api.price;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record PriceContext(
        Shop shop,
        ShopSlot slot,
        TradeSide side,
        @Nullable OfflinePlayer viewer,
        int intendedAmount,
        BigDecimal basePrice,
        Instant at,
        Map<String, Object> attrs
) {
    public static PriceContext of(Shop shop, ShopSlot slot, TradeSide side, OfflinePlayer viewer,
                                  int intendedAmount, BigDecimal basePrice) {
        return new PriceContext(shop, slot, side, viewer, intendedAmount, basePrice,
                Instant.now(), new HashMap<>());
    }
}

package me.f0reach.vshop.model;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TradeRecord(
        long id,
        Instant at,
        UUID shopId,
        UUID slotId,
        TradeSide side, // SELL or BUY
        UUID buyerUuid,
        UUID sellerUuid,
        ItemStack itemSnapshot,
        int amount,
        BigDecimal unitPrice,
        BigDecimal fee,
        BigDecimal basePrice,
        BigDecimal finalPrice,
        String resolvedBy
) {}

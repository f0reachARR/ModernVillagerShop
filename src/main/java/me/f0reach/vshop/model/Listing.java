package me.f0reach.vshop.model;

import java.time.Instant;

public record Listing(
                int listingId,
                int shopId,
                int uiSlot,
                ListingMode mode,
                byte[] itemSerialized,
                double unitPrice,
                int tradeQuantity,
                int stock,
                int targetStock,
                int cooldownSeconds,
                int lifetimeLimitPerPlayer,
                boolean enabled,
                Instant updatedAt) {
}

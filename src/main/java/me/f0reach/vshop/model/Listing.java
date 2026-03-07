package me.f0reach.vshop.model;

import java.time.Instant;

public record Listing(
        int listingId,
        int shopId,
        int uiSlot,
        ListingMode mode,
        byte[] itemSerialized,
        double unitPrice,
        int stock,
        int targetStock,
        boolean enabled,
        Instant updatedAt
) {}

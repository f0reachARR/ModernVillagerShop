package me.f0reach.vshop.model;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record Transaction(
        long txId,
        int shopId,
        int listingId,
        String direction,
        UUID buyerUuid,
        @Nullable UUID sellerUuid,
        int qty,
        double gross,
        double fee,
        double net,
        Instant createdAt
) {}

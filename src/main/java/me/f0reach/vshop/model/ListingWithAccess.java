package me.f0reach.vshop.model;

public record ListingWithAccess(
        Listing listing,
        TradeAccessSnapshot access) {
}

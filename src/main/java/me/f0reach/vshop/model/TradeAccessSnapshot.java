package me.f0reach.vshop.model;

public record TradeAccessSnapshot(
        TradeAccessBlockReason blockedReason,
        int remainingCooldownSeconds,
        int remainingLifetimeTrades) {

    public static TradeAccessSnapshot unrestricted() {
        return new TradeAccessSnapshot(TradeAccessBlockReason.NONE, 0, Integer.MAX_VALUE);
    }

    public boolean canTrade() {
        return blockedReason == TradeAccessBlockReason.NONE;
    }
}

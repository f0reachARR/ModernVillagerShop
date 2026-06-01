package me.f0reach.vshop.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class CoOwner {

    private final UUID shopId;
    private final UUID playerUuid;
    private CoOwnerRole role;
    private BigDecimal share; // percent, DECIMAL(5,2), 0.00 ~ 100.00
    private final Instant addedAt;
    private final UUID addedBy;

    public CoOwner(UUID shopId, UUID playerUuid, CoOwnerRole role, BigDecimal share, Instant addedAt, UUID addedBy) {
        this.shopId = shopId;
        this.playerUuid = playerUuid;
        this.role = role;
        this.share = share;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
    }

    public UUID shopId() { return shopId; }
    public UUID playerUuid() { return playerUuid; }
    public CoOwnerRole role() { return role; }
    public BigDecimal share() { return share; }
    public Instant addedAt() { return addedAt; }
    public UUID addedBy() { return addedBy; }

    public void setRole(CoOwnerRole role) { this.role = role; }
    public void setShare(BigDecimal share) { this.share = share; }
}

package me.f0reach.vshop.model;

public enum CoOwnerRole {
    PRIMARY,
    MANAGER,
    STAFF;

    public boolean canEditSlots() {
        return this == PRIMARY || this == MANAGER;
    }

    public boolean canManageCoOwners() {
        return this == PRIMARY;
    }

    public boolean canDeleteShop() {
        return this == PRIMARY;
    }

    public boolean canRestock() {
        return this == PRIMARY || this == MANAGER || this == STAFF;
    }

    public boolean receivesRevenue() {
        return this != STAFF;
    }

    public boolean receivesNotifications() {
        return this == PRIMARY || this == MANAGER;
    }
}

package me.f0reach.vshop.model;

import org.bukkit.entity.Villager;

import java.time.Instant;
import java.util.UUID;

/**
 * Mutable aggregate representing a shop. Storage layer persists the snapshot.
 */
public final class Shop {

    private final UUID id;
    private ShopType type;
    private UUID ownerUuid; // PRIMARY's UUID cache; nullable for admin
    private ShopLocation location;
    private UUID villagerEntityId; // current spawned villager
    private Villager.Profession profession;
    private String name;
    private boolean suspended;
    private int rowCount; // number of 9-slot rows; -1 => infinite
    private final Instant createdAt;
    private Instant updatedAt;

    public Shop(UUID id, ShopType type, UUID ownerUuid, ShopLocation location, UUID villagerEntityId,
                Villager.Profession profession, String name, boolean suspended, int rowCount,
                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.type = type;
        this.ownerUuid = ownerUuid;
        this.location = location;
        this.villagerEntityId = villagerEntityId;
        this.profession = profession;
        this.name = name;
        this.suspended = suspended;
        this.rowCount = rowCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() { return id; }
    public ShopType type() { return type; }
    public UUID ownerUuid() { return ownerUuid; }
    public ShopLocation location() { return location; }
    public UUID villagerEntityId() { return villagerEntityId; }
    public Villager.Profession profession() { return profession; }
    public String name() { return name; }
    public boolean suspended() { return suspended; }
    public int rowCount() { return rowCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public boolean isInfiniteRows() { return rowCount < 0; }
    public boolean isPlayerShop() { return type == ShopType.PLAYER; }
    public boolean isAdminShop() { return type == ShopType.ADMIN; }

    /**
     * Number of 9-slot content rows shown on a single chest page.
     * Finite shops with rowCount ≤ 6 fit in one page (rowCount rows, no nav row).
     * Finite shops with rowCount > 6 and infinite shops paginate with 5 rows per page.
     */
    public int chestContentRows() {
        if (isInfiniteRows()) return 5;
        int rows = Math.max(rowCount, 1);
        return rows <= 6 ? rows : 5;
    }

    public int chestContentSlots() { return chestContentRows() * 9; }

    /** Total chest size including the nav row when paginated. */
    public int chestInventorySize() {
        return isPaginated() ? 54 : chestContentSlots();
    }

    /** True when the chest needs prev/next navigation (rowCount > 6 or infinite). */
    public boolean isPaginated() {
        return isInfiniteRows() || rowCount > 6;
    }

    /**
     * Exclusive upper bound on valid global {@code slot_index}. Infinite shops
     * return {@link Integer#MAX_VALUE}; finite shops return {@code rowCount * 9}.
     * Used by chest UIs to filler/block out-of-bounds slots on the last page of
     * paginated-finite shops.
     */
    public int slotIndexLimit() {
        return isInfiniteRows() ? Integer.MAX_VALUE : Math.max(rowCount, 1) * 9;
    }

    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }
    public void setLocation(ShopLocation location) { this.location = location; }
    public void setVillagerEntityId(UUID villagerEntityId) { this.villagerEntityId = villagerEntityId; }
    public void setProfession(Villager.Profession profession) { this.profession = profession; }
    public void setName(String name) { this.name = name; }
    public void setSuspended(boolean suspended) { this.suspended = suspended; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setType(ShopType type) { this.type = type; }
}

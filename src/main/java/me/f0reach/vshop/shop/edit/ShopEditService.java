package me.f0reach.vshop.shop.edit;

import me.f0reach.vshop.api.event.ShopSlotChangeEvent;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authorisation + persistence helpers for the edit flow. Also tracks which
 * shops are currently being edited so the trade flow can refuse interactions
 * mid-edit (spec §5 "編集モード中は同ショップに対する購入/納品をブロックし、混在を防ぐ").
 */
public final class ShopEditService {

    private final StorageManager storage;
    private final ShopRegistry registry;
    private final Set<UUID> editingShops = ConcurrentHashMap.newKeySet();

    public ShopEditService(StorageManager storage, ShopRegistry registry) {
        this.storage = storage;
        this.registry = registry;
    }

    /** Whether the given player may open the edit UI for this shop. */
    public boolean canEdit(Player player, Shop shop) throws SQLException {
        if (player.hasPermission("modernvillagershop.edit.others")) return true;
        if (shop.isAdminShop()) {
            return player.hasPermission("modernvillagershop.admin.edit");
        }
        if (!player.hasPermission("modernvillagershop.edit")) return false;
        return roleOf(player.getUniqueId(), shop)
                .map(r -> r == CoOwnerRole.PRIMARY || r == CoOwnerRole.MANAGER)
                .orElse(false);
    }

    public java.util.Optional<CoOwnerRole> roleOf(UUID playerUuid, Shop shop) throws SQLException {
        for (CoOwner co : storage.coOwners().findByShop(shop.id())) {
            if (co.playerUuid().equals(playerUuid)) return java.util.Optional.of(co.role());
        }
        return java.util.Optional.empty();
    }

    public void persistSlot(ShopSlot slot) throws SQLException {
        boolean isCreate = storage.slots().findById(slot.id()).isEmpty();
        storage.slots().upsert(slot);
        Shop shop = registry.byId(slot.shopId()).orElse(null);
        if (shop != null) {
            Bukkit.getPluginManager().callEvent(new ShopSlotChangeEvent(shop, slot,
                    isCreate ? ShopSlotChangeEvent.Kind.CREATED : ShopSlotChangeEvent.Kind.UPDATED));
        }
    }

    public void deleteSlot(ShopSlot slot) throws SQLException {
        storage.slots().delete(slot.id());
        Shop shop = registry.byId(slot.shopId()).orElse(null);
        if (shop != null) {
            Bukkit.getPluginManager().callEvent(new ShopSlotChangeEvent(shop, slot,
                    ShopSlotChangeEvent.Kind.DELETED));
        }
    }

    public void beginEditing(UUID shopId) { editingShops.add(shopId); }
    public void endEditing(UUID shopId) { editingShops.remove(shopId); }
    public boolean isEditing(UUID shopId) { return editingShops.contains(shopId); }
}

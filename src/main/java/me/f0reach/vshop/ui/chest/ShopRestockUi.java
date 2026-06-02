package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.edit.ShopEditService;
import me.f0reach.vshop.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Opens a chest mirroring the shop's dedicated inventory. Chest slot index
 * equals {@code shop_inventory.slot_index} (spec §6 / §8.1). The player may
 * freely move stacks between their inventory and the chest; the close handler
 * diffs against the snapshot and persists changes.
 *
 * Layout: rows = max(shop.rowCount, 1) clamped to 6 for player shops; admin
 * shops have no physical stock and never open this view.
 */
public final class ShopRestockUi {

    private static final Logger LOG = Logger.getLogger(ShopRestockUi.class.getName());

    private final StorageManager storage;
    private final MessageManager messages;
    private final ShopEditService editService;

    public ShopRestockUi(StorageManager storage, MessageManager messages, ShopEditService editService) {
        this.storage = storage;
        this.messages = messages;
        this.editService = editService;
    }

    public void open(Player viewer, Shop shop) {
        if (shop.isAdminShop()) {
            viewer.sendMessage(messages.get("edit.restock.admin-shop"));
            return;
        }
        int rows = shop.isInfiniteRows() ? 6 : Math.min(6, Math.max(1, shop.rowCount()));
        int size = rows * 9;
        ShopRestockHolder holder = new ShopRestockHolder(viewer, shop.id(), size);

        Component title = messages.get("edit.restock.title",
                Placeholder.parsed("shop_name", shop.name()));
        Inventory inv = holder.createInventory(size, title);

        try {
            List<InventoryEntry> entries = storage.inventory().findByShop(shop.id());
            for (InventoryEntry e : entries) {
                if (e.slotIndex() < 0 || e.slotIndex() >= size) continue; // out-of-view stays as-is
                ItemStack template = ItemIdentity.copyTemplate(e.item());
                int displayed = Math.min(template.getMaxStackSize(), Math.max(1, e.amount()));
                ItemStack shown = template.clone();
                shown.setAmount(displayed);
                inv.setItem(e.slotIndex(), shown);
                holder.snapshotPut(e.slotIndex(), template, e.amount(), displayed);
            }
        } catch (SQLException ex) {
            LOG.warning("Failed to load shop inventory for restock UI: " + ex.getMessage());
        }

        editService.beginEditing(shop.id());
        viewer.openInventory(inv);
    }
}

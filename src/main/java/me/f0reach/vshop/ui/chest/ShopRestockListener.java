package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.edit.ShopEditService;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Click router for the paginated restock UI. The 5 top rows accept free
 * stack movement (the editor IS the inventory). The bottom nav row is
 * locked: prev/next persist the current page first, then re-paint.
 *
 * On inventory close (without a deliberate page-flip), the current page is
 * persisted and editing mode ends.
 *
 * Persistence heuristic: a chest slot can only display {@code maxStackSize}
 * at once, so if the current contents match the snapshot's clamped display
 * value AND the item template is identical, the slot is treated as unchanged
 * and its possibly-overflowing DB amount is preserved.
 */
public final class ShopRestockListener implements Listener {

    private static final Logger LOG = Logger.getLogger(ShopRestockListener.class.getName());

    private final ModernVillagerShopPlugin plugin;
    private final StorageManager storage;
    private final MessageManager messages;
    private final ShopEditService editService;
    private final PluginConfig config;
    private final ShopRestockUi restockUi;

    public ShopRestockListener(ModernVillagerShopPlugin plugin, StorageManager storage,
                               MessageManager messages, ShopEditService editService,
                               PluginConfig config, ShopRestockUi restockUi) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.editService = editService;
        this.config = config;
        this.restockUi = restockUi;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder ih = event.getInventory().getHolder();
        if (!(ih instanceof ShopRestockHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int raw = event.getRawSlot();

        // Player's own inventory: free movement. Shift-click bulk transfer
        // into the chest is intentionally allowed — nav-row and out-of-bounds
        // slots are pre-filled with panes/icons whose meta differs from
        // anything a player carries, so Bukkit's default MOVE_TO_OTHER_INVENTORY
        // only lands items in valid in-bounds content slots. Double-click
        // COLLECT_TO_CURSOR still spills across the boundary in surprising
        // ways, so we keep that one blocked. HOTBAR_SWAP from player-inv
        // hovers stays within the player's own inventory so we leave it alone.
        if (raw >= holder.inventorySize()) {
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }

        // Nav row (only present when paginated): cancel and route.
        if (holder.paginated() && raw >= holder.contentSlots()) {
            event.setCancelled(true);
            handleNav(viewer, holder, raw);
            return;
        }

        // Content rows: free movement, except out-of-bounds slots (last-page filler).
        if (!holder.isContentSlotInBounds(raw)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder ih = event.getInventory().getHolder();
        if (!(ih instanceof ShopRestockHolder holder)) return;
        // Disallow drags into the nav row or out-of-bounds content slots.
        for (int slot : event.getRawSlots()) {
            if (slot >= holder.inventorySize()) continue;
            if (holder.paginated() && slot >= holder.contentSlots()) {
                event.setCancelled(true);
                return;
            }
            if (slot < holder.contentSlots() && !holder.isContentSlotInBounds(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void handleNav(Player viewer, ShopRestockHolder holder, int raw) {
        if (raw == holder.slotClose()) {
            viewer.closeInventory();
            return;
        }
        Shop shop = plugin.registry().byId(holder.shopId()).orElse(null);
        if (shop == null) {
            holder.setSuppressReturnOnClose(true);
            viewer.closeInventory();
            return;
        }
        if (raw == holder.slotPrev()) {
            if (holder.page() <= 0) return;
            if (!persistCurrentPage(viewer, holder)) return;
            holder.setPage(holder.page() - 1);
            restockUi.paint(holder, shop);
            return;
        }
        if (raw == holder.slotNext()) {
            if (!persistCurrentPage(viewer, holder)) return;
            holder.setPage(holder.page() + 1);
            restockUi.paint(holder, shop);
        }
    }

    /**
     * Persists the chest's current page contents to DB. Returns false if any
     * error occurred; the page is NOT advanced in that case so the player
     * can fix and retry.
     */
    private boolean persistCurrentPage(Player viewer, ShopRestockHolder holder) {
        Inventory inv = holder.getInventory();
        Map<Integer, ItemStack> survivors = new HashMap<>();
        int returned = 0;
        for (int i = 0; i < holder.contentSlots(); i++) {
            if (!holder.isContentSlotInBounds(i)) continue;
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            if (ItemIdentity.isBlacklisted(config, stack)) {
                Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(stack.clone());
                returned += stack.getAmount();
                for (ItemStack ov : overflow.values()) {
                    viewer.getWorld().dropItemNaturally(viewer.getLocation(), ov);
                }
                inv.setItem(i, null);
                continue;
            }
            survivors.put(i, stack);
        }
        if (returned > 0) viewer.sendMessage(messages.get("edit.restock.returned"));

        try {
            persistDiff(holder, survivors);
            return true;
        } catch (SQLException ex) {
            LOG.severe("Restock persist failed: " + ex.getMessage());
            viewer.sendMessage(messages.get("error.generic"));
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder ih = event.getInventory().getHolder();
        if (!(ih instanceof ShopRestockHolder holder)) return;
        if (holder.saved()) return;
        holder.markSaved();
        try {
            if (holder.suppressCloseSave()) return;
            if (!(event.getPlayer() instanceof Player viewer)) return;
            try {
                if (persistCurrentPage(viewer, holder)) {
                    viewer.sendMessage(messages.get("edit.restock.saved"));
                }
            } catch (RuntimeException re) {
                LOG.severe("Restock close persist failed: " + re.getMessage());
            }
        } finally {
            editService.endEditing(holder.shopId());
            if (!holder.suppressReturnOnClose()) {
                Runnable onClose = holder.onClose();
                if (onClose != null) Bukkit.getScheduler().runTask(plugin, onClose);
            }
        }
    }

    private void persistDiff(ShopRestockHolder holder, Map<Integer, ItemStack> survivors) throws SQLException {
        var repo = storage.inventory();
        for (int i = 0; i < holder.contentSlots(); i++) {
            if (!holder.isContentSlotInBounds(i)) continue;
            ItemStack current = survivors.get(i);
            ShopRestockHolder.SnapshotEntry snap = holder.snapshotOf(i);
            int globalSlot = holder.toGlobalSlot(i);

            if (current == null) {
                if (snap != null) repo.delete(holder.shopId(), globalSlot);
                continue;
            }
            if (snap != null && ItemIdentity.sameItem(snap.template(), current)
                    && current.getAmount() == snap.displayedAmount()) {
                // Unchanged — keep the (potentially clamped-display) real amount.
                continue;
            }
            ItemStack template = current.clone();
            template.setAmount(1);
            repo.upsert(new InventoryEntry(holder.shopId(), globalSlot, template, current.getAmount()));
        }
    }
}

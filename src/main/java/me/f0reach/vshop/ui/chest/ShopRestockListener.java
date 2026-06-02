package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.shop.edit.ShopEditService;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Diffs the chest contents against the snapshot stored in
 * {@link ShopRestockHolder} and persists the change. Blacklisted items are
 * returned to the player rather than persisted.
 *
 * Notes on the unchanged-slot heuristic: a chest slot can display only
 * {@code maxStackSize} items, so a DB amount > 64 is shown clamped. To avoid
 * silently truncating, we leave that slot's DB amount untouched if (a) the
 * item template still matches and (b) the visible count equals the originally
 * displayed value.
 */
public final class ShopRestockListener implements Listener {

    private static final Logger LOG = Logger.getLogger(ShopRestockListener.class.getName());

    private final StorageManager storage;
    private final MessageManager messages;
    private final ShopEditService editService;
    private final PluginConfig config;

    public ShopRestockListener(StorageManager storage, MessageManager messages,
                               ShopEditService editService, PluginConfig config) {
        this.storage = storage;
        this.messages = messages;
        this.editService = editService;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder ih = event.getInventory().getHolder();
        if (!(ih instanceof ShopRestockHolder holder)) return;
        if (holder.saved()) return;
        holder.markSaved();
        if (!(event.getPlayer() instanceof Player viewer)) return;

        Inventory inv = holder.getInventory();
        Map<Integer, ItemStack> survivors = new HashMap<>();
        int returned = 0;
        for (int i = 0; i < holder.slotCount(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            if (ItemIdentity.isBlacklisted(config, stack)) {
                Map<Integer, ItemStack> overflow = viewer.getInventory().addItem(stack.clone());
                returned += stack.getAmount();
                // Anything that didn't fit drops at the player's feet.
                for (ItemStack ov : overflow.values()) {
                    viewer.getWorld().dropItemNaturally(viewer.getLocation(), ov);
                }
                inv.setItem(i, null);
                continue;
            }
            survivors.put(i, stack);
        }
        if (returned > 0) {
            viewer.sendMessage(messages.get("edit.restock.returned"));
        }

        try {
            persistDiff(holder, survivors);
            viewer.sendMessage(messages.get("edit.restock.saved"));
        } catch (SQLException ex) {
            LOG.severe("Restock persist failed: " + ex.getMessage());
            viewer.sendMessage(messages.get("error.generic"));
        } finally {
            editService.endEditing(holder.shopId());
        }
    }

    private void persistDiff(ShopRestockHolder holder, Map<Integer, ItemStack> survivors) throws SQLException {
        var repo = storage.inventory();
        for (int i = 0; i < holder.slotCount(); i++) {
            ItemStack current = survivors.get(i);
            ShopRestockHolder.SnapshotEntry snap = holder.snapshotOf(i);

            if (current == null) {
                if (snap != null) repo.delete(holder.shopId(), i);
                continue;
            }
            if (snap != null && ItemIdentity.sameItem(snap.template(), current)
                    && current.getAmount() == snap.displayedAmount()) {
                // Unchanged — keep the (potentially clamped-display) real amount.
                continue;
            }
            ItemStack template = current.clone();
            template.setAmount(1);
            repo.upsert(new InventoryEntry(holder.shopId(), i, template, current.getAmount()));
        }
    }
}

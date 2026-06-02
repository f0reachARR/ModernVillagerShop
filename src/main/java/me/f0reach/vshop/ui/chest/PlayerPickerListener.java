package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.model.PlayerCacheEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

/**
 * Click router for {@link PlayerPickerUi}. Cancels every click (this is a
 * read-only selector), routes the head clicks to the holder's callback, and
 * delivers cancel on inventory close if the user closed without picking.
 */
public final class PlayerPickerListener implements Listener {

    private final Plugin plugin;
    private final PlayerPickerUi ui;

    public PlayerPickerListener(Plugin plugin, PlayerPickerUi ui) {
        this.plugin = plugin;
        this.ui = ui;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder ih = event.getInventory().getHolder();
        if (!(ih instanceof PlayerPickerHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int raw = event.getRawSlot();

        if (raw == PlayerPickerUi.SLOT_PREV) {
            if (holder.page() > 0) {
                holder.setPage(holder.page() - 1);
                ui.repaint(holder);
            }
            return;
        }
        if (raw == PlayerPickerUi.SLOT_NEXT) {
            holder.setPage(holder.page() + 1);
            ui.repaint(holder);
            return;
        }
        if (raw == PlayerPickerUi.SLOT_SORT) {
            holder.setByName(!holder.byName());
            holder.setPage(0);
            ui.repaint(holder);
            return;
        }
        if (raw == PlayerPickerUi.SLOT_CANCEL) {
            viewer.closeInventory();
            return;
        }
        if (raw == PlayerPickerUi.SLOT_SEARCH) {
            holder.setSuppressCancelOnClose(true);
            viewer.closeInventory();
            ui.promptSearch(viewer, holder);
            return;
        }
        if (raw < 0 || raw >= PlayerPickerUi.CONTENT_SLOTS) return;

        var entries = holder.currentPage();
        if (entries == null || raw >= entries.size()) return;
        PlayerCacheEntry picked = entries.get(raw);
        holder.markSelected();
        viewer.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (holder.callback() != null) holder.callback().accept(picked);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PlayerPickerHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof PlayerPickerHolder holder)) return;
        if (holder.selected()) return; // explicit pick already dispatched
        if (holder.suppressCancelOnClose()) return; // intentional close (e.g. search)
        if (holder.cancelCallback() != null) {
            Bukkit.getScheduler().runTask(plugin, holder.cancelCallback());
        }
    }
}

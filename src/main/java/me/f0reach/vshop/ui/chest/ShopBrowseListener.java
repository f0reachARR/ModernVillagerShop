package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.ShopRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Routes clicks inside the shop browse view: cancels item movement, walks
 * pagination, and (later) hands off to the buy/sell confirm flow.
 */
public final class ShopBrowseListener implements Listener {

    private final ShopRegistry registry;
    private final ShopBrowseUi browseUi;

    public ShopBrowseListener(ShopRegistry registry, ShopBrowseUi browseUi) {
        this.registry = registry;
        this.browseUi = browseUi;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopBrowseHolder browseHolder)) return;

        // Browse view is read-only — never let any stack be moved.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        int raw = event.getRawSlot();

        if (raw == ShopBrowseUi.SLOT_PREV_PAGE) {
            if (browseHolder.page() > 0) {
                browseHolder.setPage(browseHolder.page() - 1);
                browseUi.repaint(viewer, browseHolder);
            }
            return;
        }
        if (raw == ShopBrowseUi.SLOT_NEXT_PAGE) {
            browseHolder.setPage(browseHolder.page() + 1);
            browseUi.repaint(viewer, browseHolder);
            return;
        }
        if (raw == ShopBrowseUi.SLOT_CLOSE) {
            viewer.closeInventory();
            return;
        }
        if (raw < 0 || raw >= ShopBrowseUi.CONTENT_SLOTS) {
            return;
        }

        // Content slot clicked → look up the slot, then open the trade dialog.
        Shop shop = registry.byId(browseHolder.shopId()).orElse(null);
        if (shop == null) {
            viewer.closeInventory();
            return;
        }
        // TODO: open buy/sell confirm dialog. The trade-flow layer wires this up.
    }
}

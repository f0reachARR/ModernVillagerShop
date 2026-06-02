package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.trade.TradeFlow;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

import java.sql.SQLException;
import java.util.List;

/**
 * Routes clicks inside the shop browse view: cancels item movement, walks
 * pagination, and dispatches slot clicks into the trade flow.
 */
public final class ShopBrowseListener implements Listener {

    private final ShopRegistry registry;
    private final ShopBrowseUi browseUi;
    private final StorageManager storage;
    private final TradeFlow tradeFlow;
    private final MessageManager messages;

    public ShopBrowseListener(ShopRegistry registry, ShopBrowseUi browseUi, StorageManager storage,
                              TradeFlow tradeFlow, MessageManager messages) {
        this.registry = registry;
        this.browseUi = browseUi;
        this.storage = storage;
        this.tradeFlow = tradeFlow;
        this.messages = messages;
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

        Shop shop = registry.byId(browseHolder.shopId()).orElse(null);
        if (shop == null) {
            viewer.closeInventory();
            return;
        }

        ShopSlot slot = findSlot(shop, browseHolder.page(), raw);
        if (slot == null) return;

        viewer.closeInventory();
        tradeFlow.start(viewer, shop, slot);
    }

    private ShopSlot findSlot(Shop shop, int page, int innerSlot) {
        int target = page * ShopBrowseUi.CONTENT_SLOTS + innerSlot;
        List<ShopSlot> slots;
        try {
            slots = storage.slots().findByShop(shop.id());
        } catch (SQLException ex) {
            return null;
        }
        for (ShopSlot s : slots) if (s.slotIndex() == target) return s;
        return null;
    }

    MessageManager messages() { return messages; }
}

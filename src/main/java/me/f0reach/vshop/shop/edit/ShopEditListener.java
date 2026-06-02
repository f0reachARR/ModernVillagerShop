package me.f0reach.vshop.shop.edit;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.ui.chest.ShopEditHolder;
import me.f0reach.vshop.ui.chest.ShopEditUi;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.List;

/**
 * Click router for the edit-mode chest UI. Handles:
 *   - empty slot + cursor item → open create dialog (item is NOT consumed)
 *   - existing slot click → open edit dialog (left) / delete dialog (shift+right)
 *   - navigation row clicks
 *   - drag events are blocked entirely (multi-slot drag is confusing here)
 */
public final class ShopEditListener implements Listener {

    private final ShopRegistry registry;
    private final ShopEditUi editUi;
    private final ShopEditService editService;
    private final SlotEditFlow slotFlow;
    private final StorageManager storage;
    private final MessageManager messages;

    public ShopEditListener(ShopRegistry registry, ShopEditUi editUi, ShopEditService editService,
                            SlotEditFlow slotFlow, StorageManager storage, MessageManager messages) {
        this.registry = registry;
        this.editUi = editUi;
        this.editService = editService;
        this.slotFlow = slotFlow;
        this.storage = storage;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopEditHolder editHolder)) return;
        // Disallow stack movement in this view; we read the cursor manually below.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player editor)) return;
        int raw = event.getRawSlot();

        if (raw == ShopEditUi.SLOT_PREV_PAGE) {
            if (editHolder.page() > 0) {
                editHolder.setPage(editHolder.page() - 1);
                editUi.repaint(editor, editHolder);
            }
            return;
        }
        if (raw == ShopEditUi.SLOT_NEXT_PAGE) {
            editHolder.setPage(editHolder.page() + 1);
            editUi.repaint(editor, editHolder);
            return;
        }
        if (raw == ShopEditUi.SLOT_CLOSE) {
            editor.closeInventory();
            return;
        }
        if (raw == ShopEditUi.SLOT_RESTOCK) {
            editor.sendMessage(messages.get("edit.restock.coming-soon"));
            return;
        }
        if (raw < 0 || raw >= ShopEditUi.CONTENT_SLOTS) {
            return;
        }

        Shop shop = registry.byId(editHolder.shopId()).orElse(null);
        if (shop == null) {
            editor.closeInventory();
            return;
        }

        int flatIndex = editHolder.page() * ShopEditUi.CONTENT_SLOTS + raw;
        ShopSlot existing = findSlot(shop, flatIndex);
        ItemStack cursor = event.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        Runnable refresh = () -> editUi.repaint(editor, editHolder);

        if (existing != null) {
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                slotFlow.openDelete(editor, shop, existing, refresh);
            } else {
                editor.closeInventory();
                slotFlow.openEdit(editor, shop, existing, () -> editUi.open(editor, shop, editHolder.page()));
            }
            return;
        }

        // Empty slot.
        if (!hasCursor) {
            editor.sendMessage(messages.get("edit.slot.place-prompt"));
            return;
        }
        // Take a snapshot — we deliberately do NOT consume the player's stack.
        ItemStack template = cursor.clone();
        template.setAmount(1);
        editor.closeInventory();
        slotFlow.openCreate(editor, shop, flatIndex, template, () -> editUi.open(editor, shop, editHolder.page()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopEditHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ShopEditHolder editHolder) {
            editService.endEditing(editHolder.shopId());
        }
    }

    private ShopSlot findSlot(Shop shop, int flatIndex) {
        List<ShopSlot> slots;
        try {
            slots = storage.slots().findByShop(shop.id());
        } catch (SQLException ex) {
            return null;
        }
        for (ShopSlot s : slots) if (s.slotIndex() == flatIndex) return s;
        return null;
    }
}

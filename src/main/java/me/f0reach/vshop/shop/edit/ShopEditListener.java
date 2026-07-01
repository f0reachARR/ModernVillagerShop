package me.f0reach.vshop.shop.edit;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.ui.chest.ShopEditHolder;
import me.f0reach.vshop.ui.chest.ShopEditUi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

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

    private final Plugin plugin;
    private final ShopRegistry registry;
    private final ShopEditUi editUi;
    private final ShopEditService editService;
    private final SlotEditFlow slotFlow;
    private final StorageManager storage;
    private final MessageManager messages;

    public ShopEditListener(Plugin plugin, ShopRegistry registry, ShopEditUi editUi,
                            ShopEditService editService, SlotEditFlow slotFlow,
                            StorageManager storage, MessageManager messages) {
        this.plugin = plugin;
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
        if (!(event.getWhoClicked() instanceof Player editor)) return;
        int raw = event.getRawSlot();

        // Clicks in the player's own inventory (raw >= top inventory size) are
        // free — players need to be able to pick items up to drop them onto an
        // empty slot to create a new entry. Block only actions that would spill
        // into the chest grid: shift-click move, double-click collect, hotbar
        // swap.
        if (raw >= editHolder.inventorySize()) {
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR
                    || action == InventoryAction.HOTBAR_SWAP) {
                event.setCancelled(true);
            }
            return;
        }

        // Inside the chest grid — we own the interaction.
        event.setCancelled(true);

        if (editHolder.paginated()) {
            if (raw == editHolder.slotPrev()) {
                if (editHolder.page() > 0) {
                    editHolder.setPage(editHolder.page() - 1);
                    editUi.repaint(editor, editHolder);
                }
                return;
            }
            if (raw == editHolder.slotNext()) {
                editHolder.setPage(editHolder.page() + 1);
                editUi.repaint(editor, editHolder);
                return;
            }
            if (raw == editHolder.slotClose()) {
                editor.closeInventory();
                return;
            }
        }
        if (raw < 0 || raw >= editHolder.contentSlots()) {
            return;
        }

        Shop shop = registry.byId(editHolder.shopId()).orElse(null);
        if (shop == null) {
            editHolder.setSuppressReturnOnClose(true);
            editor.closeInventory();
            return;
        }

        int flatIndex = editHolder.page() * editHolder.contentSlots() + raw;
        ShopSlot existing = findSlot(shop, flatIndex);
        ItemStack cursor = event.getCursor();
        boolean hasCursor = cursor != null && !cursor.getType().isAir();

        Runnable refresh = () -> editUi.repaint(editor, editHolder);
        Runnable onClose = editHolder.onClose();
        // The chest is already suppress-closed at the caller before this fires,
        // so the new open() just installs a fresh holder — the OLD holder's
        // suppress flag has already done its job.
        Runnable reopenChest = () -> editUi.open(editor, shop, editHolder.page(), onClose);

        if (existing != null) {
            if (event.getClick() == ClickType.SHIFT_RIGHT) {
                slotFlow.openDelete(editor, shop, existing, refresh);
            } else {
                editHolder.setSuppressReturnOnClose(true);
                editor.closeInventory();
                slotFlow.openEdit(editor, shop, existing, reopenChest);
            }
            return;
        }

        // Empty slot — block creation on out-of-bounds slots (last-page filler).
        if (!editHolder.isContentSlotInBounds(raw)) {
            return;
        }
        if (!hasCursor) {
            editor.sendMessage(messages.get("edit.slot.place-prompt"));
            return;
        }
        // Take a snapshot — we deliberately do NOT consume the player's stack.
        ItemStack template = cursor.clone();
        template.setAmount(1);
        editHolder.setSuppressReturnOnClose(true);
        editor.closeInventory();
        slotFlow.openCreate(editor, shop, flatIndex, template, reopenChest);
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
            if (editHolder.suppressReturnOnClose()) return;
            Runnable onClose = editHolder.onClose();
            if (onClose != null) Bukkit.getScheduler().runTask(plugin, onClose);
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

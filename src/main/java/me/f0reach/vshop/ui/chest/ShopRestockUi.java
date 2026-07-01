package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.edit.ShopEditService;
import me.f0reach.vshop.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Opens a chest mirroring the shop's dedicated inventory. Dimensions follow
 * the shop's {@code rowCount}: finite shops (1-6) get a single page sized to
 * rowCount rows; infinite shops paginate with 45 slots per page mapping chest
 * slot {@code i} on page {@code p} to {@code shop_inventory.slot_index = p*45 + i}.
 *
 * The player may freely move stacks between their inventory and the chest's
 * content area. Admin shops have no physical stock and never open this view.
 */
public final class ShopRestockUi {

    private static final Logger LOG = Logger.getLogger(ShopRestockUi.class.getName());

    private final StorageManager storage;
    private final MessageManager messages;
    private final ShopEditService editService;
    private final IconConfig icons;

    public ShopRestockUi(StorageManager storage, MessageManager messages, ShopEditService editService,
                         IconConfig icons) {
        this.storage = storage;
        this.messages = messages;
        this.editService = editService;
        this.icons = icons;
    }

    public void open(Player viewer, Shop shop) {
        open(viewer, shop, null);
    }

    public void open(Player viewer, Shop shop, Runnable onClose) {
        if (shop.isAdminShop()) {
            viewer.sendMessage(messages.get("edit.restock.admin-shop"));
            return;
        }
        ShopRestockHolder holder = new ShopRestockHolder(viewer, shop, onClose);
        Component title = messages.get("edit.restock.title",
                Placeholder.parsed("shop_name", shop.name()));
        holder.createInventory(title);
        paint(holder, shop);
        editService.beginEditing(shop.id());
        viewer.openInventory(holder.getInventory());
    }

    /** Re-paints the current page (called after navigation). */
    public void paint(ShopRestockHolder holder, Shop shop) {
        Inventory inv = holder.getInventory();
        inv.clear();
        holder.snapshotClear();

        int stride = holder.contentSlots();
        int pageStart = holder.page() * stride;
        int pageEnd = pageStart + stride;
        int maxSlotSeen = -1;
        try {
            List<InventoryEntry> entries = storage.inventory().findByShop(shop.id());
            for (InventoryEntry e : entries) {
                if (e.slotIndex() > maxSlotSeen) maxSlotSeen = e.slotIndex();
                if (e.slotIndex() < pageStart || e.slotIndex() >= pageEnd) continue;
                int chestSlot = e.slotIndex() - pageStart;
                ItemStack template = ItemIdentity.copyTemplate(e.item());
                int displayed = Math.min(template.getMaxStackSize(), Math.max(1, e.amount()));
                ItemStack shown = template.clone();
                shown.setAmount(displayed);
                inv.setItem(chestSlot, shown);
                holder.snapshotPut(chestSlot, template, e.amount(), displayed);
            }
        } catch (SQLException ex) {
            LOG.warning("Failed to load shop inventory for restock UI: " + ex.getMessage());
        }

        if (!holder.paginated()) return;

        // Fill out-of-bounds slots on the last page of a finite paginated shop.
        for (int i = 0; i < stride; i++) {
            if (!holder.isContentSlotInBounds(i) && inv.getItem(i) == null) {
                inv.setItem(i, ChestFiller.neutralPane());
            }
        }

        int maxOccupiedPage = maxSlotSeen < 0 ? 0 : maxSlotSeen / stride;
        paintNav(inv, holder, maxOccupiedPage);
    }

    private void paintNav(Inventory inv, ShopRestockHolder holder, int maxOccupiedPage) {
        // Fill the nav row with a neutral pane so the nav area looks distinct.
        ItemStack pane = ChestFiller.neutralPane();
        for (int i = holder.contentSlots(); i < holder.inventorySize(); i++) {
            inv.setItem(i, pane);
        }
        if (holder.page() > 0) {
            inv.setItem(holder.slotPrev(), iconWithFallback("prevPage", Material.ARROW, "edit.restock.prev"));
        }
        // Always allow advancing — infinite capacity, the next page may be empty but usable.
        inv.setItem(holder.slotNext(), iconWithFallback("nextPage", Material.ARROW, "edit.restock.next"));
        inv.setItem(holder.slotClose(), iconWithFallback("close", Material.BARRIER, "edit.restock.close"));
        inv.setItem(holder.slotPageIndicator(), pageIndicator(holder.page(), maxOccupiedPage));
    }

    /**
     * Looks up an {@code ui.chest.icons.<key>} override; falls back to the
     * provided material + localised display name. Centralising the lookup here
     * keeps spec §5 "ナビゲーション要素は設定可能" working for the restock view.
     */
    private ItemStack iconWithFallback(String key, Material defaultMaterial, String fallbackMessageKey) {
        String fallbackName = messages.getRaw(fallbackMessageKey);
        return icons.icon(key, defaultMaterial, fallbackName);
    }

    private ItemStack pageIndicator(int page, int maxOccupiedPage) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Page " + (page + 1), NamedTextColor.WHITE));
            meta.lore(List.of(Component.text("使用中の最終ページ: " + (maxOccupiedPage + 1),
                    NamedTextColor.DARK_GRAY)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

}

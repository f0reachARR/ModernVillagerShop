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
 * Opens a 6-row chest mirroring the shop's dedicated inventory. The chest
 * is paginated so capacity is effectively unbounded — page {@code p} maps
 * chest slot {@code i} to {@code shop_inventory.slot_index = p*45 + i}.
 *
 * The player may freely move stacks between their inventory and the chest's
 * content area (rows 1-5). The bottom row holds prev/page/close/next nav.
 * Admin shops have no physical stock and never open this view.
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
        ShopRestockHolder holder = new ShopRestockHolder(viewer, shop.id());
        Component title = messages.get("edit.restock.title",
                Placeholder.parsed("shop_name", shop.name()));
        holder.createInventory(ShopRestockHolder.INVENTORY_SIZE, title);
        paint(holder, shop);
        editService.beginEditing(shop.id());
        viewer.openInventory(holder.getInventory());
    }

    /** Re-paints the current page (called after navigation). */
    public void paint(ShopRestockHolder holder, Shop shop) {
        Inventory inv = holder.getInventory();
        inv.clear();
        holder.snapshotClear();

        int pageStart = holder.page() * ShopRestockHolder.PAGE_SIZE;
        int pageEnd = pageStart + ShopRestockHolder.PAGE_SIZE;
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

        int maxOccupiedPage = maxSlotSeen < 0 ? 0 : maxSlotSeen / ShopRestockHolder.PAGE_SIZE;
        paintNav(inv, holder, maxOccupiedPage);
    }

    private void paintNav(Inventory inv, ShopRestockHolder holder, int maxOccupiedPage) {
        // Fill the nav row with a neutral pane so the nav area looks distinct.
        ItemStack pane = filler();
        for (int i = ShopRestockHolder.PAGE_SIZE; i < ShopRestockHolder.INVENTORY_SIZE; i++) {
            inv.setItem(i, pane);
        }
        if (holder.page() > 0) {
            inv.setItem(ShopRestockHolder.SLOT_PREV, navIcon(Material.ARROW, "edit.restock.prev"));
        }
        // Always allow advancing — capacity is unbounded, the next page may be empty but usable.
        inv.setItem(ShopRestockHolder.SLOT_NEXT, navIcon(Material.ARROW, "edit.restock.next"));
        inv.setItem(ShopRestockHolder.SLOT_CLOSE, navIcon(Material.BARRIER, "edit.restock.close"));
        inv.setItem(ShopRestockHolder.SLOT_PAGE_INDICATOR, pageIndicator(holder.page(), maxOccupiedPage));
    }

    private ItemStack navIcon(Material material, String key) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(messages.get(key));
            stack.setItemMeta(meta);
        }
        return stack;
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

    private ItemStack filler() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

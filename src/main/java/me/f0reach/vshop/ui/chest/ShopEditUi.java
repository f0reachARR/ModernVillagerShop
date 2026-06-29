package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Editor counterpart to {@link ShopBrowseUi}: same slot grid but tinted
 * differently so the editor can never mistake it for the customer view.
 */
public final class ShopEditUi {

    private final StorageManager storage;
    private final IconConfig icons;
    private final MessageManager messages;
    private final MiniMessage mm;

    public ShopEditUi(StorageManager storage, IconConfig icons, MessageManager messages) {
        this.storage = storage;
        this.icons = icons;
        this.messages = messages;
        this.mm = messages.miniMessage();
    }

    public void open(Player editor, Shop shop, int page) {
        ShopEditHolder holder = new ShopEditHolder(editor, shop, page);
        Component title = mm.deserialize("<dark_red>編集: " + shop.name());
        Inventory inv = holder.createInventory(title);
        paint(inv, holder, shop);
        editor.openInventory(inv);
    }

    public void repaint(Player editor, ShopEditHolder holder) {
        Shop shop;
        try {
            shop = storage.shops().findById(holder.shopId()).orElse(null);
        } catch (SQLException ex) {
            shop = null;
        }
        if (shop == null) {
            editor.closeInventory();
            return;
        }
        paint(holder.getInventory(), holder, shop);
    }

    private void paint(Inventory inv, ShopEditHolder holder, Shop shop) {
        inv.clear();

        List<ShopSlot> slots;
        try {
            slots = storage.slots().findByShop(shop.id());
        } catch (SQLException ex) {
            slots = List.of();
        }

        List<InventoryEntry> inventoryEntries = List.of();
        if (shop.isPlayerShop()) {
            try {
                inventoryEntries = storage.inventory().findByShop(shop.id());
            } catch (SQLException ignored) {}
        }

        int stride = holder.contentSlots();
        Map<Integer, Map<Integer, ShopSlot>> byPage = new TreeMap<>();
        for (ShopSlot s : slots) {
            int p = s.slotIndex() / stride;
            int inner = s.slotIndex() % stride;
            byPage.computeIfAbsent(p, k -> new TreeMap<>()).put(inner, s);
        }

        Map<Integer, ShopSlot> pageSlots = byPage.getOrDefault(holder.page(), Map.of());
        for (int i = 0; i < stride; i++) {
            ShopSlot s = pageSlots.get(i);
            if (s != null) {
                inv.setItem(i, renderSlot(shop, s, inventoryEntries));
            }
        }

        if (!holder.paginated()) return;

        // Fill out-of-bounds slots on the last page of a finite paginated shop.
        for (int i = 0; i < stride; i++) {
            if (!holder.isContentSlotInBounds(i) && inv.getItem(i) == null) {
                inv.setItem(i, ChestFiller.neutralPane());
            }
        }

        // Navigation row — always show prev/next here; the editor may want to
        // create a new slot on an as-yet-empty page.
        inv.setItem(holder.slotPrev(), icons.icon("prevPage", Material.ARROW, "<white>Prev"));
        inv.setItem(holder.slotNext(), icons.icon("nextPage", Material.ARROW, "<white>Next"));
        inv.setItem(holder.slotClose(), icons.icon("close", Material.BARRIER, "<red>Close"));
        inv.setItem(holder.slotPageIndicator(), pageIndicator(holder.page()));
    }

    private ItemStack renderSlot(Shop shop, ShopSlot slot, List<InventoryEntry> inventoryEntries) {
        ItemStack stack = slot.itemTemplate().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), slot.unitAmount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("種別: " + slot.side(), NamedTextColor.AQUA));
            if (slot.side() == TradeSide.SELL || slot.side() == TradeSide.BOTH) {
                lore.add(Component.text("販売単価: " + slot.unitPrice() + " / " + slot.unitAmount() + "個",
                        NamedTextColor.YELLOW));
            }
            if (slot.side() == TradeSide.BUY || slot.side() == TradeSide.BOTH) {
                lore.add(Component.text("買取単価: "
                        + (slot.buyUnitPrice() == null ? slot.unitPrice() : slot.buyUnitPrice())
                        + " / 受入残: " + slot.buyCapacity(), NamedTextColor.GOLD));
            }
            if (shop.isPlayerShop()) {
                int stock = sumStock(inventoryEntries, slot.itemTemplate());
                lore.add(Component.text("在庫: " + stock + "個", NamedTextColor.GREEN));
            }
            if (slot.tradeLimit() != null) {
                lore.add(Component.text("取引上限: " + slot.tradeLimit() + " (" + slot.limitScope() + ")",
                        NamedTextColor.GRAY));
            }
            lore.add(Component.empty());
            lore.add(Component.text("[左クリックで編集]", NamedTextColor.LIGHT_PURPLE));
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static int sumStock(List<InventoryEntry> entries, ItemStack template) {
        int total = 0;
        for (InventoryEntry e : entries) {
            if (ItemIdentity.sameItem(e.item(), template)) total += e.amount();
        }
        return total;
    }

    private ItemStack pageIndicator(int page) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Page " + (page + 1), NamedTextColor.WHITE));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

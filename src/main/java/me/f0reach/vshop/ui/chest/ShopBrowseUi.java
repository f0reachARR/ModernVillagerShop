package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.shop.trade.PriceResolver;
import me.f0reach.vshop.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Opens and re-paints the read-only shop browse view. A view contains 27
 * tradable slots (matching {@code slot_index = page * 27 + slot}) and a
 * navigation row at the bottom of the chest.
 */
public final class ShopBrowseUi {

    public static final int CONTENT_SLOTS = 27;     // 3 rows
    public static final int NAV_ROW_START = 27;     // 4th row = nav
    public static final int INVENTORY_SIZE = 54;    // 6 rows total (3 content + 3 buffer/nav)

    public static final int SLOT_PREV_PAGE = 45;
    public static final int SLOT_PAGE_INDICATOR = 49;
    public static final int SLOT_NEXT_PAGE = 53;
    public static final int SLOT_CLOSE = 53 - 3;    // 50

    private final StorageManager storage;
    private final IconConfig icons;
    private final MessageManager messages;
    private final MiniMessage mm;
    private final PriceResolver priceResolver;

    public ShopBrowseUi(StorageManager storage, IconConfig icons, MessageManager messages,
                        PriceResolver priceResolver) {
        this.storage = storage;
        this.icons = icons;
        this.messages = messages;
        this.mm = messages.miniMessage();
        this.priceResolver = priceResolver;
    }

    public void open(Player viewer, Shop shop, int page) {
        ShopBrowseHolder holder = new ShopBrowseHolder(viewer, shop.id(), page);
        Component title = mm.deserialize("<dark_gray>" + shop.name());
        Inventory inv = holder.createInventory(INVENTORY_SIZE, title);
        paint(inv, holder, shop);
        viewer.openInventory(inv);
    }

    public void repaint(Player viewer, ShopBrowseHolder holder) {
        Shop shop = null;
        try {
            shop = storage.shops().findById(holder.shopId()).orElse(null);
        } catch (SQLException ignored) {}
        if (shop == null) {
            viewer.closeInventory();
            return;
        }
        paint(holder.getInventory(), holder, shop);
    }

    private void paint(Inventory inv, ShopBrowseHolder holder, Shop shop) {
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

        // Group slots by page (= slot_index / CONTENT_SLOTS).
        TreeMap<Integer, Map<Integer, ShopSlot>> byPage = new TreeMap<>();
        for (ShopSlot s : slots) {
            int p = s.slotIndex() / CONTENT_SLOTS;
            int inner = s.slotIndex() % CONTENT_SLOTS;
            byPage.computeIfAbsent(p, k -> new TreeMap<>()).put(inner, s);
        }

        Map<Integer, ShopSlot> pageSlots = byPage.getOrDefault(holder.page(), Map.of());
        for (var e : pageSlots.entrySet()) {
            inv.setItem(e.getKey(), renderSlot(shop, e.getValue(), inventoryEntries));
        }

        // Navigation row. Prev/next only render when there's somewhere to go —
        // the browse view is read-only, so hiding them avoids the empty-page click.
        int maxPage = byPage.isEmpty() ? 0 : byPage.lastKey();
        ItemStack pageIcon = pageIndicator(holder.page());
        inv.setItem(SLOT_PAGE_INDICATOR, pageIcon);
        if (holder.page() > 0) {
            inv.setItem(SLOT_PREV_PAGE, icons.icon("prevPage", Material.ARROW, "<white>Prev"));
        }
        if (holder.page() < maxPage) {
            inv.setItem(SLOT_NEXT_PAGE, icons.icon("nextPage", Material.ARROW, "<white>Next"));
        }
        inv.setItem(SLOT_CLOSE, icons.icon("close", Material.BARRIER, "<red>Close"));
    }

    private ItemStack renderSlot(Shop shop, ShopSlot slot, List<InventoryEntry> inventoryEntries) {
        ItemStack stack = slot.itemTemplate().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), slot.unitAmount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("種別: " + slot.side(), NamedTextColor.AQUA));
        Component sellReason = null;
        Component buyReason = null;
        if (slot.side() == TradeSide.SELL || slot.side() == TradeSide.BOTH) {
            PriceResolver.Resolution sell = priceResolver.resolve(shop, slot, TradeSide.SELL, null,
                    slot.unitAmount());
            BigDecimal sellPrice = sell.finalPrice();
            lore.add(Component.text("販売単価: " + sellPrice + " / " + slot.unitAmount() + "個",
                    NamedTextColor.YELLOW));
            sellReason = sell.reason();
        }
        if (slot.side() == TradeSide.BUY || slot.side() == TradeSide.BOTH) {
            PriceResolver.Resolution buy = priceResolver.resolve(shop, slot, TradeSide.BUY, null,
                    slot.unitAmount());
            BigDecimal buyPrice = buy.finalPrice();
            lore.add(Component.text("買取単価: " + buyPrice + " / 受入残: " + slot.buyCapacity(),
                    NamedTextColor.GOLD));
            buyReason = buy.reason();
        }
        if (shop.isPlayerShop()) {
            int stock = sumStock(inventoryEntries, slot.itemTemplate());
            lore.add(Component.text("在庫: " + stock + "個", NamedTextColor.GREEN));
        }
        if (slot.tradeLimit() != null) {
            lore.add(Component.text("取引上限: " + slot.tradeLimit() + " (" + slot.limitScope() + ")",
                    NamedTextColor.GRAY));
        }
        if (sellReason != null) lore.add(sellReason);
        if (buyReason != null && (sellReason == null || !buyReason.equals(sellReason))) lore.add(buyReason);
        meta.lore(lore);
        stack.setItemMeta(meta);
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

    public MessageManager messages() { return messages; }
}

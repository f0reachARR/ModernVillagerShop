package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.locale.MessageManager;
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

    public ShopBrowseUi(StorageManager storage, IconConfig icons, MessageManager messages) {
        this.storage = storage;
        this.icons = icons;
        this.messages = messages;
        this.mm = messages.miniMessage();
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

        // Group slots by page (= slot_index / CONTENT_SLOTS).
        Map<Integer, Map<Integer, ShopSlot>> byPage = new TreeMap<>();
        for (ShopSlot s : slots) {
            int p = s.slotIndex() / CONTENT_SLOTS;
            int inner = s.slotIndex() % CONTENT_SLOTS;
            byPage.computeIfAbsent(p, k -> new TreeMap<>()).put(inner, s);
        }

        Map<Integer, ShopSlot> pageSlots = byPage.getOrDefault(holder.page(), Map.of());
        for (var e : pageSlots.entrySet()) {
            inv.setItem(e.getKey(), renderSlot(e.getValue()));
        }

        // Navigation row.
        ItemStack pageIcon = pageIndicator(holder.page());
        inv.setItem(SLOT_PAGE_INDICATOR, pageIcon);
        inv.setItem(SLOT_PREV_PAGE, icons.icon("prevPage", Material.ARROW, "<white>Prev"));
        inv.setItem(SLOT_NEXT_PAGE, icons.icon("nextPage", Material.ARROW, "<white>Next"));
        inv.setItem(SLOT_CLOSE, icons.icon("close", Material.BARRIER, "<red>Close"));
    }

    private ItemStack renderSlot(ShopSlot slot) {
        ItemStack stack = slot.itemTemplate().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), slot.unitAmount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

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
        if (slot.tradeLimit() != null) {
            lore.add(Component.text("取引上限: " + slot.tradeLimit() + " (" + slot.limitScope() + ")",
                    NamedTextColor.GRAY));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
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

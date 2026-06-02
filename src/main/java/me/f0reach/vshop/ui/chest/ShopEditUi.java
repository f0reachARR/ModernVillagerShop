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
 * Editor counterpart to {@link ShopBrowseUi}: same slot grid but tinted
 * differently so the editor can never mistake it for the customer view.
 */
public final class ShopEditUi {

    public static final int CONTENT_SLOTS = 27;
    public static final int INVENTORY_SIZE = 54;
    public static final int SLOT_PREV_PAGE = 45;
    public static final int SLOT_PAGE_INDICATOR = 49;
    public static final int SLOT_CLOSE = 50;
    public static final int SLOT_NEXT_PAGE = 53;
    public static final int SLOT_RESTOCK = 46;

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
        ShopEditHolder holder = new ShopEditHolder(editor, shop.id(), page);
        Component title = mm.deserialize("<dark_red>編集: " + shop.name());
        Inventory inv = holder.createInventory(INVENTORY_SIZE, title);
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

        Map<Integer, Map<Integer, ShopSlot>> byPage = new TreeMap<>();
        for (ShopSlot s : slots) {
            int p = s.slotIndex() / CONTENT_SLOTS;
            int inner = s.slotIndex() % CONTENT_SLOTS;
            byPage.computeIfAbsent(p, k -> new TreeMap<>()).put(inner, s);
        }

        Map<Integer, ShopSlot> pageSlots = byPage.getOrDefault(holder.page(), Map.of());
        for (int i = 0; i < CONTENT_SLOTS; i++) {
            ShopSlot s = pageSlots.get(i);
            if (s == null) {
                inv.setItem(i, emptyMarker());
            } else {
                inv.setItem(i, renderSlot(s));
            }
        }

        // Navigation row
        inv.setItem(SLOT_PREV_PAGE, icons.icon("prevPage", Material.ARROW, "<white>Prev"));
        inv.setItem(SLOT_NEXT_PAGE, icons.icon("nextPage", Material.ARROW, "<white>Next"));
        inv.setItem(SLOT_CLOSE, icons.icon("close", Material.BARRIER, "<red>Close"));
        inv.setItem(SLOT_PAGE_INDICATOR, pageIndicator(holder.page()));
        inv.setItem(SLOT_RESTOCK, restockIcon());
    }

    private ItemStack renderSlot(ShopSlot slot) {
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

    private ItemStack emptyMarker() {
        ItemStack stack = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("空きスロット", NamedTextColor.DARK_GRAY));
            meta.lore(List.of(
                    Component.text("アイテムをドロップして", NamedTextColor.GRAY),
                    Component.text("新しい出品枠を作成します。", NamedTextColor.GRAY)
            ));
            stack.setItemMeta(meta);
        }
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

    private ItemStack restockIcon() {
        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("在庫補充", NamedTextColor.GREEN));
            meta.lore(List.of(Component.text("クリックで在庫チェストを開く", NamedTextColor.GRAY)));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

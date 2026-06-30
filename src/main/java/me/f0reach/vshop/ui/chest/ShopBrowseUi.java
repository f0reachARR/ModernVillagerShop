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
 * Opens and re-paints the read-only shop browse view. Chest dimensions are
 * derived from the shop's {@code rowCount} — finite shops show all slots in
 * one page; infinite shops paginate with 45 slots per page.
 */
public final class ShopBrowseUi {

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
        ShopBrowseHolder holder = new ShopBrowseHolder(viewer, shop, page);
        Component title = mm.deserialize("<dark_gray>" + shop.name());
        Inventory inv = holder.createInventory(title);
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

        int stride = holder.contentSlots();
        TreeMap<Integer, Map<Integer, ShopSlot>> byPage = new TreeMap<>();
        for (ShopSlot s : slots) {
            int p = s.slotIndex() / stride;
            int inner = s.slotIndex() % stride;
            byPage.computeIfAbsent(p, k -> new TreeMap<>()).put(inner, s);
        }

        Map<Integer, ShopSlot> pageSlots = byPage.getOrDefault(holder.page(), Map.of());
        for (var e : pageSlots.entrySet()) {
            inv.setItem(e.getKey(), renderSlot(shop, e.getValue(), inventoryEntries));
        }

        if (!holder.paginated()) return;

        // Fill out-of-bounds slots on the last page of a finite paginated shop.
        for (int i = 0; i < holder.contentSlots(); i++) {
            if (!holder.isContentSlotInBounds(i) && inv.getItem(i) == null) {
                inv.setItem(i, ChestFiller.neutralPane());
            }
        }

        int maxPage = byPage.isEmpty() ? 0 : byPage.lastKey();
        inv.setItem(holder.slotPageIndicator(), pageIndicator(holder.page()));
        if (holder.page() > 0) {
            inv.setItem(holder.slotPrev(), icons.icon("prevPage", Material.ARROW, "<white>Prev"));
        }
        if (holder.page() < maxPage) {
            inv.setItem(holder.slotNext(), icons.icon("nextPage", Material.ARROW, "<white>Next"));
        }
        inv.setItem(holder.slotClose(), icons.icon("close", Material.BARRIER, "<red>Close"));
    }

    private ItemStack renderSlot(Shop shop, ShopSlot slot, List<InventoryEntry> inventoryEntries) {
        ItemStack stack = slot.itemTemplate().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), slot.unitAmount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        boolean sellEnabled = slot.side() == TradeSide.SELL || slot.side() == TradeSide.BOTH;
        boolean buyEnabled = slot.side() == TradeSide.BUY || slot.side() == TradeSide.BOTH;
        int stock = shop.isPlayerShop() ? sumStock(inventoryEntries, slot.itemTemplate()) : Integer.MAX_VALUE;
        boolean sellOutOfStock = sellEnabled && shop.isPlayerShop() && stock < slot.unitAmount();
        boolean buyFull = buyEnabled && slot.buyCapacity() <= 0;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("種別: " + slot.side(), NamedTextColor.AQUA));
        Component sellReason = null;
        Component buyReason = null;
        if (sellEnabled) {
            PriceResolver.Resolution sell = priceResolver.resolve(shop, slot, TradeSide.SELL, null,
                    slot.unitAmount());
            BigDecimal sellPrice = sell.finalPrice();
            Component line = Component.text("販売単価: " + sellPrice + " / " + slot.unitAmount() + "個",
                    sellOutOfStock ? NamedTextColor.RED : NamedTextColor.YELLOW);
            lore.add(line);
            if (sellOutOfStock) lore.add(Component.text("在庫切れ", NamedTextColor.RED));
            sellReason = sell.reason();
        }
        if (buyEnabled) {
            PriceResolver.Resolution buy = priceResolver.resolve(shop, slot, TradeSide.BUY, null,
                    slot.unitAmount());
            BigDecimal buyPrice = buy.finalPrice();
            Component line = Component.text("買取単価: " + buyPrice + " / 受入残: " + slot.buyCapacity(),
                    buyFull ? NamedTextColor.RED : NamedTextColor.GOLD);
            lore.add(line);
            if (buyFull) lore.add(Component.text("受入満杯", NamedTextColor.RED));
            buyReason = buy.reason();
        }
        if (shop.isPlayerShop()) {
            lore.add(Component.text("在庫: " + stock + "個",
                    sellOutOfStock ? NamedTextColor.RED : NamedTextColor.GREEN));
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

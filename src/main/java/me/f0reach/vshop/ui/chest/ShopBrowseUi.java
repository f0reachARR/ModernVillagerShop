package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.shop.trade.PriceResolver;
import me.f0reach.vshop.storage.StorageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
    private final PriceResolver priceResolver;
    private final EconomyService economy;

    public ShopBrowseUi(StorageManager storage, IconConfig icons, MessageManager messages,
                        PriceResolver priceResolver, EconomyService economy) {
        this.storage = storage;
        this.icons = icons;
        this.messages = messages;
        this.priceResolver = priceResolver;
        this.economy = economy;
    }

    public void open(Player viewer, Shop shop, int page) {
        open(viewer, shop, page, null);
    }

    public void open(Player viewer, Shop shop, int page, Runnable onClose) {
        ShopBrowseHolder holder = new ShopBrowseHolder(viewer, shop, page, onClose);
        Component title = messages.get("chest.browse-title",
                Placeholder.parsed("shop_name", shop.name()));
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
            inv.setItem(holder.slotPrev(), icons.icon("prevPage", Material.ARROW,
                    messages.getRaw("chest.prev")));
        }
        if (holder.page() < maxPage) {
            inv.setItem(holder.slotNext(), icons.icon("nextPage", Material.ARROW,
                    messages.getRaw("chest.next")));
        }
        inv.setItem(holder.slotClose(), icons.icon("close", Material.BARRIER,
                messages.getRaw("chest.close")));
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
        boolean buyFull = buyEnabled && !slot.isBuyCapacityUnlimited() && slot.buyCapacity() <= 0;

        List<Component> lore = new ArrayList<>();
        lore.add(messages.get("slot.side",
                Placeholder.parsed("side", slot.side().name())));
        Component sellReason = null;
        Component buyReason = null;
        if (sellEnabled) {
            PriceResolver.Resolution sell = priceResolver.resolve(shop, slot, TradeSide.SELL, null,
                    slot.unitAmount());
            BigDecimal sellPrice = sell.finalPrice();
            lore.add(messages.get(sellOutOfStock ? "slot.sell-line-out-of-stock" : "slot.sell-line",
                    Placeholder.parsed("price", economy.format(sellPrice)),
                    Placeholder.parsed("amount", Integer.toString(slot.unitAmount()))));
            if (sellOutOfStock) lore.add(messages.get("slot.out-of-stock"));
            sellReason = sell.reason();
        }
        if (buyEnabled) {
            PriceResolver.Resolution buy = priceResolver.resolve(shop, slot, TradeSide.BUY, null,
                    slot.unitAmount());
            BigDecimal buyPrice = buy.finalPrice();
            String capacityLabel = slot.isBuyCapacityUnlimited()
                    ? messages.getRaw("slot.capacity-unlimited")
                    : Integer.toString(slot.buyCapacity());
            lore.add(messages.get(buyFull ? "slot.buy-line-full" : "slot.buy-line",
                    Placeholder.parsed("price", economy.format(buyPrice)),
                    Placeholder.parsed("capacity", capacityLabel)));
            if (buyFull) lore.add(messages.get("slot.buy-full"));
            buyReason = buy.reason();
        }
        if (shop.isPlayerShop()) {
            lore.add(messages.get(sellOutOfStock ? "slot.stock-empty" : "slot.stock",
                    Placeholder.parsed("stock", Integer.toString(stock))));
        }
        if (slot.tradeLimit() != null) {
            lore.add(messages.get("slot.limit-line",
                    Placeholder.parsed("limit", Integer.toString(slot.tradeLimit())),
                    Placeholder.parsed("scope", slot.limitScope().name())));
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
            meta.displayName(messages.get("chest.page-indicator",
                    Placeholder.parsed("page", Integer.toString(page + 1))));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public MessageManager messages() { return messages; }
}

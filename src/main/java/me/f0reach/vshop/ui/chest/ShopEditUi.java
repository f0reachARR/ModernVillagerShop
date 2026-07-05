package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
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
 * Editor counterpart to {@link ShopBrowseUi}: same slot grid but tinted
 * differently so the editor can never mistake it for the customer view.
 */
public final class ShopEditUi {

    private final StorageManager storage;
    private final IconConfig icons;
    private final MessageManager messages;
    private final EconomyService economy;

    public ShopEditUi(StorageManager storage, IconConfig icons, MessageManager messages,
                      EconomyService economy) {
        this.storage = storage;
        this.icons = icons;
        this.messages = messages;
        this.economy = economy;
    }

    public void open(Player editor, Shop shop, int page) {
        open(editor, shop, page, null);
    }

    public void open(Player editor, Shop shop, int page, Runnable onClose) {
        ShopEditHolder holder = new ShopEditHolder(editor, shop, page, onClose);
        Component title = messages.get("chest.edit-title",
                Placeholder.parsed("shop_name", shop.name()));
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
        inv.setItem(holder.slotPrev(), icons.icon("prevPage", Material.ARROW,
                messages.getRaw("chest.prev")));
        inv.setItem(holder.slotNext(), icons.icon("nextPage", Material.ARROW,
                messages.getRaw("chest.next")));
        inv.setItem(holder.slotClose(), icons.icon("close", Material.BARRIER,
                messages.getRaw("chest.close")));
        inv.setItem(holder.slotPageIndicator(), pageIndicator(holder.page()));
    }

    private ItemStack renderSlot(Shop shop, ShopSlot slot, List<InventoryEntry> inventoryEntries) {
        ItemStack stack = slot.itemTemplate().clone();
        stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), slot.unitAmount())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(messages.get("slot.side",
                    Placeholder.parsed("side", slot.side().name())));
            if (slot.side() == TradeSide.SELL || slot.side() == TradeSide.BOTH) {
                lore.add(messages.get("slot.sell-line",
                        Placeholder.parsed("price", economy.format(slot.unitPrice())),
                        Placeholder.parsed("amount", Integer.toString(slot.unitAmount()))));
            }
            if (slot.side() == TradeSide.BUY || slot.side() == TradeSide.BOTH) {
                BigDecimal buyPrice = slot.buyUnitPrice() == null ? slot.unitPrice() : slot.buyUnitPrice();
                String capacityLabel = slot.isBuyCapacityUnlimited()
                        ? messages.getRaw("slot.capacity-unlimited")
                        : Integer.toString(slot.buyCapacity());
                lore.add(messages.get("slot.buy-line",
                        Placeholder.parsed("price", economy.format(buyPrice)),
                        Placeholder.parsed("capacity", capacityLabel)));
            }
            if (shop.isPlayerShop()) {
                int stock = sumStock(inventoryEntries, slot.itemTemplate());
                lore.add(messages.get("slot.stock",
                        Placeholder.parsed("stock", Integer.toString(stock))));
            }
            if (slot.tradeLimit() != null) {
                lore.add(messages.get("slot.limit-line",
                        Placeholder.parsed("limit", Integer.toString(slot.tradeLimit())),
                        Placeholder.parsed("scope", slot.limitScope().name())));
            }
            lore.add(Component.empty());
            lore.add(messages.get("slot.click-edit"));
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
            meta.displayName(messages.get("chest.page-indicator",
                    Placeholder.parsed("page", Integer.toString(page + 1))));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

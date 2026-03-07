package me.f0reach.vshop.ui.inventory.item;

import me.f0reach.vshop.locale.MessageManager;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class NavigationItems {
    private NavigationItems() {}

    public static ItemStack prevPage(MessageManager messages) {
        return createButton(Material.ARROW, messages.get("ui.prev_page"));
    }

    public static ItemStack nextPage(MessageManager messages) {
        return createButton(Material.ARROW, messages.get("ui.next_page"));
    }

    public static ItemStack pageInfo(MessageManager messages, int page, int maxPage) {
        return createButton(Material.PAPER, messages.get("ui.page_info",
                "page", String.valueOf(page),
                "max_page", String.valueOf(maxPage)));
    }

    public static ItemStack sortButton(MessageManager messages, SortFilterState.SortOrder order) {
        String sortKey = switch (order) {
            case NAME_ASC -> messages.getRaw("ui.sort_name_asc");
            case NAME_DESC -> messages.getRaw("ui.sort_name_desc");
            case PRICE_ASC -> messages.getRaw("ui.sort_price_asc");
            case PRICE_DESC -> messages.getRaw("ui.sort_price_desc");
        };
        return createButton(Material.HOPPER, messages.get("ui.sort_button", "sort", sortKey));
    }

    public static ItemStack filterButton(MessageManager messages, SortFilterState.FilterMode mode) {
        String filterKey = switch (mode) {
            case ALL -> messages.getRaw("ui.filter_all");
            case SELL_ONLY -> messages.getRaw("ui.filter_sell");
            case BUY_ONLY -> messages.getRaw("ui.filter_buy");
        };
        return createButton(Material.COMPASS, messages.get("ui.filter_button", "filter", filterKey));
    }

    public static ItemStack openStorage(MessageManager messages) {
        return createButton(Material.CHEST, messages.get("shop.open_storage_button"));
    }

    public static ItemStack backToListings(MessageManager messages) {
        return createButton(Material.BOOK, messages.get("shop.back_to_listings_button"));
    }

    public static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack createButton(Material material, net.kyori.adventure.text.Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }
}

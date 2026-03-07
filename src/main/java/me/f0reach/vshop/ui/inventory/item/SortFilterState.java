package me.f0reach.vshop.ui.inventory.item;

import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingMode;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class SortFilterState {
    public enum SortOrder {
        NAME_ASC, NAME_DESC, PRICE_ASC, PRICE_DESC;

        public SortOrder next() {
            SortOrder[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public enum FilterMode {
        ALL, SELL_ONLY, BUY_ONLY;

        public FilterMode next() {
            FilterMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    private SortOrder sortOrder = SortOrder.NAME_ASC;
    private FilterMode filterMode = FilterMode.ALL;

    public SortOrder getSortOrder() { return sortOrder; }
    public FilterMode getFilterMode() { return filterMode; }

    public void cycleSortOrder() { sortOrder = sortOrder.next(); }
    public void cycleFilterMode() { filterMode = filterMode.next(); }

    public List<Listing> apply(List<Listing> listings) {
        return listings.stream()
                .filter(l -> switch (filterMode) {
                    case ALL -> true;
                    case SELL_ONLY -> l.mode() == ListingMode.SELL;
                    case BUY_ONLY -> l.mode() == ListingMode.BUY;
                })
                .sorted(getComparator())
                .collect(Collectors.toList());
    }

    private Comparator<Listing> getComparator() {
        return switch (sortOrder) {
            case NAME_ASC -> Comparator.comparing(l -> getItemName(l));
            case NAME_DESC -> Comparator.comparing((Listing l) -> getItemName(l)).reversed();
            case PRICE_ASC -> Comparator.comparingDouble(Listing::unitPrice);
            case PRICE_DESC -> Comparator.comparingDouble(Listing::unitPrice).reversed();
        };
    }

    private static String getItemName(Listing listing) {
        try {
            ItemStack item = ItemStack.deserializeBytes(listing.itemSerialized());
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(meta.displayName());
            }
            return item.getType().name();
        } catch (Exception e) {
            return "";
        }
    }
}

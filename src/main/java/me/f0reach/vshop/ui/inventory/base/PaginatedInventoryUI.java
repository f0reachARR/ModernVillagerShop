package me.f0reach.vshop.ui.inventory.base;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.ui.inventory.item.NavigationItems;
import me.f0reach.vshop.ui.inventory.item.SortFilterState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public abstract class PaginatedInventoryUI extends BaseInventoryUI {
    protected static final int ROWS = 6;
    protected static final int SIZE = ROWS * 9;
    protected static final int CONTENT_SLOTS = 45; // rows 0-4
    protected static final int NAV_ROW_START = 45;

    // Nav bar slot positions
    protected static final int SLOT_PREV = 45;
    protected static final int SLOT_PAGE_INFO = 49;
    protected static final int SLOT_NEXT = 53;
    protected static final int SLOT_SORT = 47;
    protected static final int SLOT_FILTER = 51;

    protected final MessageManager messages;
    protected final SortFilterState sortFilter = new SortFilterState();
    protected int currentPage = 0;
    protected List<Listing> filteredListings;

    protected PaginatedInventoryUI(Player viewer, Component title, MessageManager messages) {
        super(viewer, SIZE, title);
        this.messages = messages;
    }

    protected abstract List<Listing> getListings();
    protected abstract void renderContentSlots(List<Listing> pageListings, int offset);
    protected abstract void handleContentClick(InventoryClickEvent event, int slot, Listing listing);

    @Override
    protected void render() {
        inventory.clear();
        List<Listing> all = getListings();
        filteredListings = sortFilter.apply(all);

        int maxPage = Math.max(0, (filteredListings.size() - 1) / CONTENT_SLOTS);
        if (currentPage > maxPage) currentPage = maxPage;

        int offset = currentPage * CONTENT_SLOTS;
        int end = Math.min(offset + CONTENT_SLOTS, filteredListings.size());
        List<Listing> pageListings = filteredListings.subList(offset, end);

        renderContentSlots(pageListings, offset);
        renderNavBar(maxPage);
    }

    private void renderNavBar(int maxPage) {
        // Fill nav bar with filler
        for (int i = NAV_ROW_START; i < SIZE; i++) {
            inventory.setItem(i, NavigationItems.filler());
        }

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, NavigationItems.prevPage(messages));
        }
        inventory.setItem(SLOT_PAGE_INFO, NavigationItems.pageInfo(messages, currentPage + 1, maxPage + 1));
        if (currentPage < maxPage) {
            inventory.setItem(SLOT_NEXT, NavigationItems.nextPage(messages));
        }
        inventory.setItem(SLOT_SORT, NavigationItems.sortButton(messages, sortFilter.getSortOrder()));
        inventory.setItem(SLOT_FILTER, NavigationItems.filterButton(messages, sortFilter.getFilterMode()));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;

        if (slot < CONTENT_SLOTS) {
            int index = currentPage * CONTENT_SLOTS + slot;
            if (filteredListings != null && index < filteredListings.size()) {
                handleContentClick(event, slot, filteredListings.get(index));
            }
            return;
        }

        // Nav bar clicks
        switch (slot) {
            case SLOT_PREV -> {
                if (currentPage > 0) { currentPage--; render(); }
            }
            case SLOT_NEXT -> {
                currentPage++;
                render();
            }
            case SLOT_SORT -> {
                sortFilter.cycleSortOrder();
                currentPage = 0;
                render();
            }
            case SLOT_FILTER -> {
                sortFilter.cycleFilterMode();
                currentPage = 0;
                render();
            }
        }
    }
}

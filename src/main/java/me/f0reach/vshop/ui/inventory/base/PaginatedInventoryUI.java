package me.f0reach.vshop.ui.inventory.base;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.ui.inventory.item.NavigationItems;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class PaginatedInventoryUI extends BaseInventoryUI {
    protected static final int ROWS = 6;
    protected static final int SIZE = ROWS * 9;
    protected static final int CONTENT_SLOTS = 45;
    protected static final int NAV_ROW_START = 45;

    protected static final int SLOT_PREV = 45;
    protected static final int SLOT_PAGE_INFO = 49;
    protected static final int SLOT_NEXT = 53;

    protected final MessageManager messages;
    protected final Map<Integer, Listing> listingBySlot = new HashMap<>();
    protected int currentPage = 0;

    protected PaginatedInventoryUI(Player viewer, Component title, MessageManager messages) {
        super(viewer, SIZE, title);
        this.messages = messages;
    }

    protected abstract List<Listing> getListings();
    protected abstract void renderContentSlots(Map<Integer, Listing> pageListings);
    protected abstract void handleContentClick(InventoryClickEvent event, int slot, Listing listing);
    protected void handleEmptyContentClick(InventoryClickEvent event, int slot) {}
    protected boolean hasTrailingCreatePage() { return false; }

    @Override
    protected void render() {
        inventory.clear();
        listingBySlot.clear();
        List<Listing> listings = getListings();
        int maxUsedSlot = listings.stream()
                .mapToInt(Listing::uiSlot)
                .filter(slot -> slot >= 0)
                .max()
                .orElse(-1);
        int maxDataPage = maxUsedSlot < 0 ? 0 : (maxUsedSlot / CONTENT_SLOTS);
        int maxPage = maxDataPage + (hasTrailingCreatePage() ? 1 : 0);
        if (currentPage > maxPage) {
            currentPage = maxPage;
        }

        for (Listing listing : listings) {
            int absoluteSlot = listing.uiSlot();
            if (absoluteSlot < 0) {
                continue;
            }
            if ((absoluteSlot / CONTENT_SLOTS) != currentPage) {
                continue;
            }
            int displaySlot = absoluteSlot % CONTENT_SLOTS;
            listingBySlot.put(displaySlot, listing);
        }

        renderContentSlots(listingBySlot);
        renderNavBar(maxPage);
    }

    private void renderNavBar(int maxPage) {
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
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= SIZE) return;
        event.setCancelled(true);

        if (slot < CONTENT_SLOTS) {
            Listing listing = listingBySlot.get(slot);
            if (listing != null) {
                handleContentClick(event, slot, listing);
            } else {
                handleEmptyContentClick(event, slot);
            }
            return;
        }

        switch (slot) {
            case SLOT_PREV -> {
                if (currentPage > 0) {
                    currentPage--;
                    render();
                }
            }
            case SLOT_NEXT -> {
                currentPage++;
                render();
            }
        }
    }

    protected int toAbsoluteSlot(int contentSlot) {
        return currentPage * CONTENT_SLOTS + contentSlot;
    }
}

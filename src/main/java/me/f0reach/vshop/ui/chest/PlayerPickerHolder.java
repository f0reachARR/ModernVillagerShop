package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.model.PlayerCacheEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Per-session state for the player-picker UI. The {@code callback} is invoked
 * with the selected player's UUID on click; the picker view closes itself.
 */
public final class PlayerPickerHolder implements InventoryHolder {

    private final UUID viewerId;
    private final Consumer<PlayerCacheEntry> callback;
    private final Runnable cancelCallback;
    private int page;
    private boolean byName;
    private String query;
    private List<PlayerCacheEntry> currentPage;
    private Inventory inventory;
    private boolean selected;
    private boolean suppressCancelOnClose;

    public PlayerPickerHolder(Player viewer, Consumer<PlayerCacheEntry> callback, Runnable cancelCallback) {
        this.viewerId = viewer.getUniqueId();
        this.callback = callback;
        this.cancelCallback = cancelCallback;
    }

    public Inventory createInventory(int size, net.kyori.adventure.text.Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
        return this.inventory;
    }

    @Override public Inventory getInventory() { return inventory; }
    public UUID viewerId() { return viewerId; }
    public Consumer<PlayerCacheEntry> callback() { return callback; }
    public Runnable cancelCallback() { return cancelCallback; }
    public int page() { return page; }
    public void setPage(int page) { this.page = page; }
    public boolean byName() { return byName; }
    public void setByName(boolean byName) { this.byName = byName; }
    public String query() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<PlayerCacheEntry> currentPage() { return currentPage; }
    public void setCurrentPage(List<PlayerCacheEntry> page) { this.currentPage = page; }
    public boolean selected() { return selected; }
    public void markSelected() { this.selected = true; }
    public boolean suppressCancelOnClose() { return suppressCancelOnClose; }
    public void setSuppressCancelOnClose(boolean suppress) { this.suppressCancelOnClose = suppress; }
}

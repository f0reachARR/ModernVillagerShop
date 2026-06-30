package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.PlayerCacheEntry;
import me.f0reach.vshop.shop.cache.PlayerCacheService;
import me.f0reach.vshop.ui.dialog.DialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Chest UI for selecting a player from the {@code player_cache}. Layout:
 *   rows 0-4 (45 slots): player heads (paginated)
 *   row 5: prev / search / sort / cancel / next
 */
public final class PlayerPickerUi {

    public static final int CONTENT_SLOTS = 45;
    public static final int INVENTORY_SIZE = 54;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_SEARCH = 47;
    public static final int SLOT_SORT = 49;
    public static final int SLOT_CANCEL = 51;
    public static final int SLOT_NEXT = 53;

    private static final DateTimeFormatter LAST_SEEN_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PlayerCacheService cache;
    private final MessageManager messages;
    private final DialogService dialogs;
    private final IconConfig icons;

    public PlayerPickerUi(PlayerCacheService cache, MessageManager messages, DialogService dialogs,
                          IconConfig icons) {
        this.cache = cache;
        this.messages = messages;
        this.dialogs = dialogs;
        this.icons = icons;
    }

    public void open(Player viewer, Consumer<PlayerCacheEntry> onPick, Runnable onCancel) {
        PlayerPickerHolder holder = new PlayerPickerHolder(viewer, onPick, onCancel);
        Component title = messages.get("player-picker.title");
        Inventory inv = holder.createInventory(INVENTORY_SIZE, title);
        paint(holder, inv);
        viewer.openInventory(inv);
    }

    public void repaint(PlayerPickerHolder holder) {
        if (holder.getInventory() == null) return;
        paint(holder, holder.getInventory());
    }

    private void paint(PlayerPickerHolder holder, Inventory inv) {
        inv.clear();
        List<PlayerCacheEntry> entries;
        if (holder.query() != null && !holder.query().isEmpty()) {
            entries = cache.search(holder.query(), CONTENT_SLOTS);
        } else {
            entries = cache.page(holder.page() * CONTENT_SLOTS, CONTENT_SLOTS, holder.byName());
        }
        // Pin online players to the top per spec §5; the underlying repo can't
        // know who's online so we do it here.
        entries = sortOnlineFirst(entries);
        holder.setCurrentPage(entries);

        for (int i = 0; i < entries.size() && i < CONTENT_SLOTS; i++) {
            inv.setItem(i, renderHead(entries.get(i)));
        }

        inv.setItem(SLOT_PREV, iconWithFallback("prevPage", Material.ARROW, "player-picker.prev"));
        inv.setItem(SLOT_NEXT, iconWithFallback("nextPage", Material.ARROW, "player-picker.next"));
        inv.setItem(SLOT_CANCEL, iconWithFallback("close", Material.BARRIER, "player-picker.cancel"));
        String searchKey = holder.query() == null || holder.query().isEmpty()
                ? "player-picker.search" : "player-picker.search-active";
        inv.setItem(SLOT_SEARCH, iconWithFallback("filter", Material.OAK_SIGN, searchKey));
        String sortKey = holder.byName() ? "player-picker.sort-name" : "player-picker.sort-recent";
        inv.setItem(SLOT_SORT, iconWithFallback("sort", Material.COMPARATOR, sortKey));
    }

    private static List<PlayerCacheEntry> sortOnlineFirst(List<PlayerCacheEntry> entries) {
        List<PlayerCacheEntry> online = new ArrayList<>();
        List<PlayerCacheEntry> offline = new ArrayList<>();
        for (PlayerCacheEntry e : entries) {
            if (Bukkit.getPlayer(e.playerUuid()) != null) online.add(e);
            else offline.add(e);
        }
        online.addAll(offline);
        return online;
    }

    @SuppressWarnings("deprecation")
    private ItemStack renderHead(PlayerCacheEntry entry) {
        boolean cacheHasSkin = entry.textureValue() != null;
        boolean playerOnline = Bukkit.getPlayer(entry.playerUuid()) != null;
        if (!cacheHasSkin && !playerOnline) {
            // Fully unknown skin — show the configured fallback head so we
            // don't leak the "untextured Steve" default.
            ItemStack fallback = icons.icon("unknownPlayer", Material.PLAYER_HEAD,
                    "<aqua>" + entry.name() + "</aqua>");
            ItemMeta meta = fallback.getItemMeta();
            if (meta != null) {
                meta.lore(loreFor(entry));
                fallback.setItemMeta(meta);
            }
            return fallback;
        }
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        if (meta != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(entry.playerUuid());
            meta.setOwningPlayer(op);
            meta.displayName(Component.text(entry.name(), NamedTextColor.AQUA));
            meta.lore(loreFor(entry));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private List<Component> loreFor(PlayerCacheEntry entry) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("UUID: " + entry.playerUuid().toString().substring(0, 8),
                NamedTextColor.DARK_GRAY));
        if (entry.lastSeen() != null) {
            String formatted = entry.lastSeen()
                    .atZone(ZoneId.systemDefault())
                    .format(LAST_SEEN_FMT);
            lore.add(messages.get("player-picker.last-seen",
                    Placeholder.parsed("last_seen", formatted)));
        } else {
            lore.add(messages.get("player-picker.head-lore"));
        }
        return lore;
    }

    private ItemStack iconWithFallback(String key, Material defaultMaterial, String fallbackMessageKey) {
        String fallbackName = messages.getRaw(fallbackMessageKey);
        return icons.icon(key, defaultMaterial, fallbackName);
    }

    /** Prompts the player for a search query via InputDialog. */
    public void promptSearch(Player viewer, PlayerPickerHolder holder) {
        dialogs.input(viewer,
                        messages.get("player-picker.search-title"),
                        messages.get("player-picker.search-body"),
                        messages.get("player-picker.search-submit"))
                .text("q", messages.get("player-picker.search-label"),
                        holder.query() == null ? "" : holder.query())
                .onSubmit(response -> {
                    String q = response.getText("q").trim();
                    holder.setQuery(q.isEmpty() ? null : q);
                    holder.setPage(0);
                    // The inventory was implicitly closed by the dialog; reopen.
                    open(viewer, holder.callback(), holder.cancelCallback(), holder);
                });
    }

    /** Internal: re-open with carried-over state. */
    private void open(Player viewer, Consumer<PlayerCacheEntry> onPick, Runnable onCancel,
                      PlayerPickerHolder previous) {
        PlayerPickerHolder next = new PlayerPickerHolder(viewer, onPick, onCancel);
        next.setByName(previous.byName());
        next.setPage(previous.page());
        next.setQuery(previous.query());
        Component title = messages.get("player-picker.title");
        Inventory inv = next.createInventory(INVENTORY_SIZE, title);
        paint(next, inv);
        viewer.openInventory(inv);
    }
}

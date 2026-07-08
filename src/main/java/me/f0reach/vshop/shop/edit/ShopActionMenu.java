package me.f0reach.vshop.shop.edit;

import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.coowner.CoOwnerFlow;
import me.f0reach.vshop.shop.egg.SpawnEggMeta;
import me.f0reach.vshop.ui.chest.ShopRestockUi;
import me.f0reach.vshop.ui.dialog.DialogService;
import me.f0reach.vshop.ui.text.Displays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MultiButton-style "what would you like to do?" menu shown when the owner
 * (or a privileged co-owner) right-clicks the villager or runs {@code /vshop
 * edit}. Each button hands off to an existing UI: slot editor, browse view,
 * restock chest, co-owner manager, suspend toggle, etc.
 *
 * Permission gating is per-button rather than per-menu so that lower-rank
 * roles see only what they're allowed to do. Toggle-style actions reopen the
 * menu so the player can flip several switches without dismissing it.
 */
public final class ShopActionMenu {

    private static final Logger LOG = Logger.getLogger(ShopActionMenu.class.getName());
    private static final DateTimeFormatter HISTORY_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ModernVillagerShopPlugin plugin;
    private final DialogService dialogs;
    private final MessageManager messages;
    private final ShopEditService editService;
    private final ShopRestockUi restockUi;
    private final CoOwnerFlow coOwnerFlow;

    public ShopActionMenu(ModernVillagerShopPlugin plugin, DialogService dialogs, MessageManager messages,
                          ShopEditService editService, ShopRestockUi restockUi, CoOwnerFlow coOwnerFlow) {
        this.plugin = plugin;
        this.dialogs = dialogs;
        this.messages = messages;
        this.editService = editService;
        this.restockUi = restockUi;
        this.coOwnerFlow = coOwnerFlow;
    }

    public boolean canShow(Player viewer, Shop shop) {
        try {
            return editService.canEdit(viewer, shop);
        } catch (SQLException ex) {
            return false;
        }
    }

    public void open(Player viewer, Shop shop) {
        boolean isPrimary = isPrimary(viewer, shop);

        Component title = messages.get("action.title",
                Placeholder.parsed("shop_name", shop.name()));
        Component body = messages.get("action.body",
                Placeholder.parsed("type", shop.type().name()),
                Placeholder.parsed("suspended", shop.suspended() ? "yes" : "no"));

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();

        buttons.add(new DialogService.ButtonSpec(messages.get("action.edit-slots"),
                () -> openSlotEditor(viewer, shop)));
        buttons.add(new DialogService.ButtonSpec(messages.get("action.preview"),
                () -> plugin.browseUi().open(viewer, shop, 0, () -> open(viewer, shop))));
        if (shop.isPlayerShop() && hasAnyPerm(viewer, "modernvillagershop.edit",
                "modernvillagershop.edit.others", "modernvillagershop.admin.edit")) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.restock"),
                    () -> restockUi.open(viewer, shop, () -> open(viewer, shop))));
        }
        buttons.add(new DialogService.ButtonSpec(messages.get("action.info-menu"),
                () -> openInfoSubmenu(viewer, shop)));
        buttons.add(new DialogService.ButtonSpec(messages.get("action.settings-menu"),
                () -> openSettingsSubmenu(viewer, shop)));
        if (canShowOwnerMenu(viewer, shop, isPrimary)) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.owner-menu"),
                    () -> openOwnerSubmenu(viewer, shop)));
        }

        dialogs.multiButton(viewer, title, body, buttons);
    }

    private void openInfoSubmenu(Player viewer, Shop shop) {
        Component title = messages.get("action.info.title",
                Placeholder.parsed("shop_name", shop.name()));
        Component body = messages.get("action.info.body");

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();
        buttons.add(new DialogService.ButtonSpec(messages.get("action.history"),
                () -> showRecentHistory(viewer, shop)));
        buttons.add(new DialogService.ButtonSpec(messages.get("action.stats"),
                () -> showStats(viewer, shop)));
        buttons.add(new DialogService.ButtonSpec(messages.get("dialog.back"),
                () -> open(viewer, shop)));

        dialogs.multiButton(viewer, title, body, buttons, () -> open(viewer, shop));
    }

    private void openSettingsSubmenu(Player viewer, Shop shop) {
        boolean notifyOn = readNotifyPref(viewer);
        Component title = messages.get("action.settings.title",
                Placeholder.parsed("shop_name", shop.name()));
        Component body = messages.get("action.settings.body");

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();
        if (hasAnyPerm(viewer, "modernvillagershop.edit.rename",
                "modernvillagershop.edit.others", "modernvillagershop.admin.edit")) {
            buttons.add(new DialogService.ButtonSpec(
                    messages.get("action.rename.button",
                            Placeholder.parsed("current", shop.name())),
                    () -> openRename(viewer, shop)));
        }
        if (hasAnyPerm(viewer, "modernvillagershop.edit.profession",
                "modernvillagershop.edit.others", "modernvillagershop.admin.edit")) {
            buttons.add(new DialogService.ButtonSpec(
                    messages.get("action.profession.button",
                            Placeholder.parsed("current", professionLabel(shop.profession()))),
                    () -> openProfession(viewer, shop)));
        }
        buttons.add(new DialogService.ButtonSpec(
                messages.get("action.notifications",
                        Placeholder.parsed("current",
                                messages.getRaw(notifyOn ? "action.state-on" : "action.state-off"))),
                () -> toggleNotifications(viewer, shop)));
        if (hasAnyPerm(viewer, "modernvillagershop.edit.suspend",
                "modernvillagershop.edit.others", "modernvillagershop.admin.edit")) {
            buttons.add(new DialogService.ButtonSpec(
                    shop.suspended() ? messages.get("action.resume") : messages.get("action.suspend"),
                    () -> toggleSuspended(viewer, shop)));
        }
        buttons.add(new DialogService.ButtonSpec(messages.get("dialog.back"),
                () -> open(viewer, shop)));

        dialogs.multiButton(viewer, title, body, buttons, () -> open(viewer, shop));
    }

    private void openOwnerSubmenu(Player viewer, Shop shop) {
        boolean isPrimary = isPrimary(viewer, shop);
        Component title = messages.get("action.owner.title",
                Placeholder.parsed("shop_name", shop.name()));
        Component body = messages.get("action.owner.body");

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();
        if (shop.isPlayerShop() && hasAnyPerm(viewer,
                "modernvillagershop.coowner.manage", "modernvillagershop.coowner.manage.others")) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.coowner"),
                    () -> coOwnerFlow.openManager(viewer, shop, () -> openOwnerSubmenu(viewer, shop))));
        }
        if (shop.isPlayerShop() && hasAnyPerm(viewer,
                "modernvillagershop.coowner.transfer", "modernvillagershop.coowner.transfer.others")) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.transfer"),
                    () -> coOwnerFlow.openTransferWithPicker(viewer, shop,
                            () -> openOwnerSubmenu(viewer, shop))));
        }
        if (canShowDelete(viewer, shop, isPrimary)) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.delete.button"),
                    () -> openDelete(viewer, shop)));
        }
        buttons.add(new DialogService.ButtonSpec(messages.get("dialog.back"),
                () -> open(viewer, shop)));

        dialogs.multiButton(viewer, title, body, buttons, () -> open(viewer, shop));
    }

    private boolean canShowOwnerMenu(Player viewer, Shop shop, boolean isPrimary) {
        if (shop.isPlayerShop() && hasAnyPerm(viewer,
                "modernvillagershop.coowner.manage", "modernvillagershop.coowner.manage.others")) {
            return true;
        }
        if (shop.isPlayerShop() && hasAnyPerm(viewer,
                "modernvillagershop.coowner.transfer", "modernvillagershop.coowner.transfer.others")) {
            return true;
        }
        return canShowDelete(viewer, shop, isPrimary);
    }

    // Delete is dangerous — gate on permission AND PRIMARY role (or override).
    private boolean canShowDelete(Player viewer, Shop shop, boolean isPrimary) {
        return hasAnyPerm(viewer, "modernvillagershop.edit.delete", "modernvillagershop.edit.others",
                "modernvillagershop.admin.edit") && (isPrimary || shop.isAdminShop()
                        || viewer.hasPermission("modernvillagershop.edit.others")
                        || viewer.hasPermission("modernvillagershop.admin.edit"));
    }

    private void openSlotEditor(Player viewer, Shop shop) {
        try {
            if (!editService.canEdit(viewer, shop)) {
                viewer.sendMessage(messages.get("shop.edit.no-permission"));
                return;
            }
        } catch (SQLException ex) {
            viewer.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            return;
        }
        editService.beginEditing(shop.id());
        plugin.editUi().open(viewer, shop, 0, () -> open(viewer, shop));
        viewer.sendMessage(messages.get("shop.edit.editing",
                Placeholder.parsed("shop_name", shop.name())));
    }

    private void toggleSuspended(Player viewer, Shop shop) {
        boolean next = !shop.suspended();
        shop.setSuspended(next);
        try {
            plugin.shopService().update(shop);
            viewer.sendMessage(messages.get(next ? "shop.suspended" : "shop.resumed",
                    Placeholder.parsed("shop_id", shop.id().toString().substring(0, 8))));
        } catch (SQLException ex) {
            LOG.log(Level.SEVERE, "suspend toggle failed", ex);
            viewer.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            shop.setSuspended(!next); // rollback in-memory
        }
        // Re-open the settings submenu so the player can keep flipping switches.
        openSettingsSubmenu(viewer, shop);
    }

    private void toggleNotifications(Player viewer, Shop shop) {
        var repo = plugin.storage().playerPreferences();
        try {
            boolean current = repo.wantsNotifications(viewer.getUniqueId());
            repo.setWantsNotifications(viewer.getUniqueId(), !current);
            viewer.sendMessage(messages.get(!current
                    ? "action.notifications-on" : "action.notifications-off"));
        } catch (SQLException ex) {
            viewer.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
        }
        openSettingsSubmenu(viewer, shop);
    }

    private void openRename(Player viewer, Shop shop) {
        dialogs.input(viewer,
                        messages.get("action.rename.title"),
                        messages.get("action.rename.body",
                                Placeholder.parsed("current", shop.name())),
                        messages.get("action.rename.submit"))
                .text("name", messages.get("action.rename.label"), shop.name())
                .onCancel(() -> openSettingsSubmenu(viewer, shop))
                .onSubmit(response -> {
                    String next = response.getText("name").trim();
                    if (next.isEmpty()) {
                        viewer.sendMessage(messages.get("action.rename.invalid"));
                        openSettingsSubmenu(viewer, shop);
                        return;
                    }
                    shop.setName(next);
                    try {
                        plugin.shopService().update(shop);
                        plugin.villagerManager().refreshDisplayName(shop);
                        viewer.sendMessage(messages.get("action.rename.done",
                                Placeholder.parsed("name", next)));
                    } catch (SQLException ex) {
                        LOG.log(Level.SEVERE, "rename failed", ex);
                        viewer.sendMessage(messages.get("error.generic",
                                Placeholder.parsed("reason", ex.getMessage())));
                    }
                    openSettingsSubmenu(viewer, shop);
                });
    }

    private void openProfession(Player viewer, Shop shop) {
        List<Villager.Profession> all = new ArrayList<>();
        for (Villager.Profession p : Registry.VILLAGER_PROFESSION) all.add(p);
        List<DialogService.InputBuilder.Option> options = new ArrayList<>(all.size());
        int currentIdx = 0;
        for (int i = 0; i < all.size(); i++) {
            Villager.Profession p = all.get(i);
            options.add(new DialogService.InputBuilder.Option(p.getKey().toString(),
                    Component.text(professionLabel(p))));
            if (shop.profession() != null && p.equals(shop.profession())) currentIdx = i;
        }
        dialogs.input(viewer,
                        messages.get("action.profession.title"),
                        messages.get("action.profession.body",
                                Placeholder.parsed("current", professionLabel(shop.profession()))),
                        messages.get("action.profession.submit"))
                .dropdown("profession", messages.get("action.profession.label"), options, currentIdx)
                .onCancel(() -> openSettingsSubmenu(viewer, shop))
                .onSubmit(response -> {
                    NamespacedKey key = NamespacedKey.fromString(
                            response.getDropdownOptionId("profession"));
                    Villager.Profession chosen = key == null ? null
                            : Registry.VILLAGER_PROFESSION.get(key);
                    if (chosen == null) {
                        viewer.sendMessage(messages.get("error.generic",
                                Placeholder.parsed("reason", "invalid profession")));
                        openSettingsSubmenu(viewer, shop);
                        return;
                    }
                    shop.setProfession(chosen);
                    try {
                        plugin.shopService().update(shop);
                        Villager v = plugin.villagerManager().findEntity(shop);
                        if (v != null) plugin.villagerManager().refresh(v, shop, plugin.pluginConfig());
                        viewer.sendMessage(messages.get("action.profession.done",
                                Placeholder.parsed("profession", professionLabel(chosen))));
                    } catch (SQLException ex) {
                        LOG.log(Level.SEVERE, "profession update failed", ex);
                        viewer.sendMessage(messages.get("error.generic",
                                Placeholder.parsed("reason", ex.getMessage())));
                    }
                    openSettingsSubmenu(viewer, shop);
                });
    }

    private static String professionLabel(Villager.Profession profession) {
        if (profession == null) return "NONE";
        return profession.getKey().getKey();
    }

    private void openDelete(Player viewer, Shop shop) {
        int stockCount = countStock(shop);
        PluginConfig.CloseWithInventoryMode mode = plugin.pluginConfig().shop().closeWithInventory();
        String warnKey = switch (mode) {
            case DISCARD -> "action.delete.warn-discard";
            case DROP -> "action.delete.warn-drop";
            case REFUSE -> "action.delete.warn-refuse";
        };
        Component warning = stockCount > 0
                ? messages.get(warnKey, Placeholder.parsed("count", Integer.toString(stockCount)))
                : Component.empty();

        dialogs.confirmOnce(viewer,
                messages.get("action.delete.title"),
                messages.get("action.delete.body",
                        Placeholder.parsed("shop_name", shop.name()),
                        Placeholder.component("warning", warning)),
                messages.get("action.delete.yes"),
                messages.get("action.delete.no"),
                () -> {
                    try {
                        ShopService.DeleteResult result = plugin.shopService().delete(shop);
                        String shortId = shop.id().toString().substring(0, 8);
                        switch (result) {
                            case DELETED -> {
                                viewer.sendMessage(messages.get("shop.deleted",
                                        Placeholder.parsed("shop_id", shortId)));
                                refundEggIfAllowed(viewer, shop);
                            }
                            case DROPPED -> {
                                viewer.sendMessage(messages.get("shop.deleted-dropped",
                                        Placeholder.parsed("shop_id", shortId)));
                                refundEggIfAllowed(viewer, shop);
                            }
                            case BLOCKED_HAS_INVENTORY -> {
                                viewer.sendMessage(messages.get("shop.delete-blocked-has-inventory",
                                        Placeholder.parsed("shop_id", shortId)));
                                openOwnerSubmenu(viewer, shop);
                            }
                        }
                    } catch (SQLException ex) {
                        LOG.log(Level.SEVERE, "delete failed", ex);
                        viewer.sendMessage(messages.get("error.generic",
                                Placeholder.parsed("reason", ex.getMessage())));
                        openOwnerSubmenu(viewer, shop);
                    }
                },
                () -> openOwnerSubmenu(viewer, shop),
                () -> openOwnerSubmenu(viewer, shop));
    }

    // Drop a spawn egg matching the deleted shop's original type at the shop
    // location, if the viewer holds the refund permission. Skipped for admin
    // shops so admin eggs can't be farmed via moderation deletes.
    private void refundEggIfAllowed(Player viewer, Shop shop) {
        if (!viewer.hasPermission("modernvillagershop.edit.delete.refund")) return;
        if (shop.isAdminShop()) return;
        SpawnEggMeta meta = shop.isInfiniteRows()
                ? SpawnEggMeta.ofInfinitePlayer()
                : SpawnEggMeta.ofFixedRows(Math.max(1, shop.rowCount()));
        ItemStack egg = plugin.eggFactory().create(meta);
        World world = Bukkit.getWorld(shop.location().worldId());
        if (world == null) return;
        Location at = new Location(world, shop.location().x(), shop.location().y(), shop.location().z());
        world.dropItemNaturally(at, egg);
        viewer.sendMessage(messages.get("shop.deleted-refunded",
                Placeholder.parsed("type", meta.displayType())));
    }

    private int countStock(Shop shop) {
        try {
            int total = 0;
            for (InventoryEntry entry : plugin.storage().inventory().findByShop(shop.id())) {
                if (entry.item() != null && entry.amount() > 0) total += entry.amount();
            }
            return total;
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "failed to read inventory for delete-confirm warning", ex);
            return 0;
        }
    }

    private void showRecentHistory(Player viewer, Shop shop) {
        try {
            List<TradeRecord> recent = plugin.storage().transactions()
                    .findByShop(shop.id(), 10, 0);
            if (recent.isEmpty()) {
                viewer.sendMessage(messages.get("history.empty"));
                return;
            }
            viewer.sendMessage(messages.get("history.header"));
            for (TradeRecord rec : recent) {
                String counterparty = resolveCounterparty(rec);
                Component line = messages.get("history.line",
                        Placeholder.parsed("time", HISTORY_FORMAT.format(rec.at())),
                        Placeholder.parsed("side", rec.side().name()),
                        Placeholder.parsed("amount", Integer.toString(rec.amount())),
                        Placeholder.component("item", Displays.item(rec.itemSnapshot())),
                        Placeholder.parsed("price", plugin.economyService().format(rec.unitPrice())),
                        Placeholder.parsed("counterparty", counterparty));
                viewer.sendMessage(line);
            }
        } catch (SQLException ex) {
            viewer.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
        }
    }

    private String resolveCounterparty(TradeRecord rec) {
        // SELL = ショップが売却 → buyer がカウンタパーティ (取引した相手)
        // BUY = ショップが買取 → seller がカウンタパーティ
        UUID other = rec.side() == me.f0reach.vshop.model.TradeSide.SELL
                ? rec.buyerUuid() : rec.sellerUuid();
        if (other == null) return "-";
        var entry = plugin.playerCacheService().findByUuid(other).orElse(null);
        if (entry != null) return entry.name();
        var op = org.bukkit.Bukkit.getOfflinePlayer(other);
        return op.getName() != null ? op.getName() : other.toString().substring(0, 8);
    }

    private void showStats(Player viewer, Shop shop) {
        try {
            var agg = plugin.api().statsFor(shop.id());
            int slotCount = plugin.storage().slots().findByShop(shop.id()).size();
            viewer.sendMessage(messages.get("stats.header",
                    Placeholder.parsed("shop_name", shop.name())));
            viewer.sendMessage(messages.get("stats.slots",
                    Placeholder.parsed("count", String.valueOf(slotCount))));
            viewer.sendMessage(messages.get("stats.totals",
                    Placeholder.parsed("sell_count", String.valueOf(agg.sellCount())),
                    Placeholder.parsed("buy_count", String.valueOf(agg.buyCount())),
                    Placeholder.parsed("sell_total", plugin.economyService().format(agg.totalSalesValue())),
                    Placeholder.parsed("buy_total", plugin.economyService().format(agg.totalBuyValue()))));
        } catch (SQLException ex) {
            viewer.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
        }
    }

    private boolean readNotifyPref(Player viewer) {
        try {
            return plugin.storage().playerPreferences().wantsNotifications(viewer.getUniqueId());
        } catch (SQLException ex) {
            return true;
        }
    }

    private boolean isPrimary(Player viewer, Shop shop) {
        try {
            Optional<CoOwnerRole> role = editService.roleOf(viewer.getUniqueId(), shop);
            return role.isPresent() && role.get() == CoOwnerRole.PRIMARY;
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean hasAnyPerm(Player viewer, String... perms) {
        for (String p : perms) if (viewer.hasPermission(p)) return true;
        return false;
    }
}

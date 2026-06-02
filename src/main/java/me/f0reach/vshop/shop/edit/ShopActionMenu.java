package me.f0reach.vshop.shop.edit;

import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeRecord;
import me.f0reach.vshop.shop.coowner.CoOwnerFlow;
import me.f0reach.vshop.ui.chest.ShopRestockUi;
import me.f0reach.vshop.ui.dialog.DialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MultiButton-style "what would you like to do?" menu shown when the owner
 * (or a privileged co-owner) right-clicks the villager or runs {@code /vshop
 * edit}. Each button hands off to an existing UI: slot editor, browse view,
 * restock chest, co-owner manager, suspend toggle, etc.
 *
 * Permission gating is per-button rather than per-menu so that lower-rank
 * roles see only what they're allowed to do.
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
        Component title = messages.get("action.title",
                Placeholder.parsed("shop_name", shop.name()));
        Component body = messages.get("action.body",
                Placeholder.parsed("type", shop.type().name()),
                Placeholder.parsed("suspended", shop.suspended() ? "yes" : "no"));

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();

        buttons.add(new DialogService.ButtonSpec(messages.get("action.edit-slots"),
                () -> openSlotEditor(viewer, shop)));
        buttons.add(new DialogService.ButtonSpec(messages.get("action.preview"),
                () -> plugin.browseUi().open(viewer, shop, 0)));
        if (shop.isPlayerShop() && hasAnyPerm(viewer, "modernvillagershop.edit",
                "modernvillagershop.edit.others", "modernvillagershop.admin.edit")) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.restock"),
                    () -> restockUi.open(viewer, shop)));
        }
        if (shop.isPlayerShop() && hasAnyPerm(viewer,
                "modernvillagershop.coowner.manage", "modernvillagershop.coowner.manage.others")) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.coowner"),
                    () -> coOwnerFlow.openManager(viewer, shop)));
        }
        if (shop.isPlayerShop() && hasAnyPerm(viewer,
                "modernvillagershop.coowner.transfer", "modernvillagershop.coowner.transfer.others")) {
            buttons.add(new DialogService.ButtonSpec(messages.get("action.transfer"),
                    () -> coOwnerFlow.openTransferWithPicker(viewer, shop)));
        }
        buttons.add(new DialogService.ButtonSpec(messages.get("action.notifications"),
                () -> toggleNotifications(viewer)));
        buttons.add(new DialogService.ButtonSpec(messages.get("action.history"),
                () -> showRecentHistory(viewer, shop)));
        buttons.add(new DialogService.ButtonSpec(messages.get("action.stats"),
                () -> showStats(viewer, shop)));
        if (hasAnyPerm(viewer, "modernvillagershop.edit", "modernvillagershop.edit.others",
                "modernvillagershop.admin.edit")) {
            buttons.add(new DialogService.ButtonSpec(
                    shop.suspended() ? messages.get("action.resume") : messages.get("action.suspend"),
                    () -> toggleSuspended(viewer, shop)));
        }

        dialogs.multiButton(viewer, title, body, buttons);
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
        plugin.editUi().open(viewer, shop, 0);
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
    }

    private void toggleNotifications(Player viewer) {
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
                viewer.sendMessage(Component.text("[" + HISTORY_FORMAT.format(rec.at()) + "] "
                                + rec.side() + " " + rec.amount() + "× " + rec.itemSnapshot().getType().name()
                                + " @ " + rec.unitPrice(),
                        NamedTextColor.GRAY));
            }
        } catch (SQLException ex) {
            viewer.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
        }
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

    private static boolean hasAnyPerm(Player viewer, String... perms) {
        for (String p : perms) if (viewer.hasPermission(p)) return true;
        return false;
    }
}

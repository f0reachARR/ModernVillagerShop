package me.f0reach.vshop.shop.coowner;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.ShopVillagerManager;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.ui.dialog.DialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * UI driver for {@code /vshop coowner} and {@code /vshop transfer}. Uses Multi-
 * ButtonDialog for the member list and InputDialog for name/share input.
 * Player selection is by name (resolved via {@link Bukkit#getOfflinePlayer(String)})
 * — the spec also describes a chest-based player picker backed by {@code player_cache},
 * which is a separate v1 deliverable.
 */
public final class CoOwnerFlow {

    private static final Logger LOG = Logger.getLogger(CoOwnerFlow.class.getName());
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final DialogService dialogs;
    private final MessageManager messages;
    private final StorageManager storage;
    private final ShopService shopService;
    private final ShopVillagerManager villagerManager;

    public CoOwnerFlow(DialogService dialogs, MessageManager messages, StorageManager storage,
                       ShopService shopService, ShopVillagerManager villagerManager) {
        this.dialogs = dialogs;
        this.messages = messages;
        this.storage = storage;
        this.shopService = shopService;
        this.villagerManager = villagerManager;
    }

    public void openManager(Player primary, Shop shop) {
        if (!primary.hasPermission("modernvillagershop.coowner.manage")
                && !primary.hasPermission("modernvillagershop.coowner.manage.others")) {
            primary.sendMessage(messages.get("coowner.no-permission"));
            return;
        }
        List<CoOwner> coOwners;
        try {
            coOwners = storage.coOwners().findByShop(shop.id());
        } catch (SQLException ex) {
            primary.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            return;
        }

        if (!primary.hasPermission("modernvillagershop.coowner.manage.others")) {
            boolean isPrimary = coOwners.stream().anyMatch(c ->
                    c.playerUuid().equals(primary.getUniqueId()) && c.role() == CoOwnerRole.PRIMARY);
            if (!isPrimary) {
                primary.sendMessage(messages.get("coowner.no-permission"));
                return;
            }
        }

        showList(primary, shop, coOwners);
    }

    public void openTransfer(Player primary, Shop shop, String targetName) {
        if (!primary.hasPermission("modernvillagershop.coowner.transfer")
                && !primary.hasPermission("modernvillagershop.coowner.transfer.others")) {
            primary.sendMessage(messages.get("coowner.no-permission"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId().equals(primary.getUniqueId())) {
            primary.sendMessage(messages.get("coowner.transfer.same-player"));
            return;
        }

        Component body = messages.get("coowner.transfer.body",
                Placeholder.parsed("player", targetName),
                Placeholder.parsed("shop_name", shop.name()));
        dialogs.confirmOnce(primary,
                messages.get("coowner.transfer.title"), body,
                messages.get("coowner.transfer.yes"), messages.get("coowner.transfer.no"),
                () -> performTransfer(primary, shop, target),
                () -> {});
    }

    // ---- list / add / edit / remove ----

    private void showList(Player primary, Shop shop, List<CoOwner> coOwners) {
        Component title = messages.get("coowner.list.title",
                Placeholder.parsed("shop_name", shop.name()));
        Component body = messages.get("coowner.list.body");

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();
        for (CoOwner co : coOwners) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(co.playerUuid());
            String label = (p.getName() != null ? p.getName() : co.playerUuid().toString().substring(0, 8))
                    + " [" + co.role() + " " + co.share().toPlainString() + "%]";
            buttons.add(new DialogService.ButtonSpec(Component.text(label),
                    () -> showMemberActions(primary, shop, co)));
        }
        buttons.add(new DialogService.ButtonSpec(messages.get("coowner.list.add"),
                () -> showAdd(primary, shop)));
        dialogs.multiButton(primary, title, body, buttons);
    }

    private void showAdd(Player primary, Shop shop) {
        dialogs.input(primary,
                        messages.get("coowner.add.title"),
                        messages.get("coowner.add.body"),
                        messages.get("coowner.add.submit"))
                .text("name", messages.get("coowner.add.name-label"), "")
                .dropdown("role", messages.get("coowner.add.role-label"),
                        List.of(
                                new DialogService.InputBuilder.Option("MANAGER", Component.text("MANAGER")),
                                new DialogService.InputBuilder.Option("STAFF", Component.text("STAFF"))
                        ), 0)
                .text("share", messages.get("coowner.add.share-label"), "0.00")
                .onSubmit(response -> {
                    String name = response.getText("name").trim();
                    if (name.isEmpty()) {
                        primary.sendMessage(messages.get("coowner.add.invalid-name"));
                        return;
                    }
                    OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                    if (target.getUniqueId().equals(primary.getUniqueId())) {
                        primary.sendMessage(messages.get("coowner.add.invalid-name"));
                        return;
                    }
                    CoOwnerRole role = CoOwnerRole.valueOf(response.getDropdownOptionId("role"));
                    BigDecimal share = role == CoOwnerRole.STAFF
                            ? BigDecimal.ZERO : parseShare(response.getText("share"));
                    persistAndRebalance(primary, shop, new CoOwner(shop.id(), target.getUniqueId(),
                            role, share, Instant.now(), primary.getUniqueId()), /*remove*/ false);
                });
    }

    private void showMemberActions(Player primary, Shop shop, CoOwner co) {
        if (co.role() == CoOwnerRole.PRIMARY) {
            primary.sendMessage(messages.get("coowner.primary-immutable"));
            return;
        }
        Component title = messages.get("coowner.member.title");
        OfflinePlayer p = Bukkit.getOfflinePlayer(co.playerUuid());
        Component body = messages.get("coowner.member.body",
                Placeholder.parsed("player", p.getName() == null ? co.playerUuid().toString() : p.getName()),
                Placeholder.parsed("role", co.role().name()),
                Placeholder.parsed("share", co.share().toPlainString()));
        dialogs.multiButton(primary, title, body, List.of(
                new DialogService.ButtonSpec(messages.get("coowner.member.edit"),
                        () -> showEditMember(primary, shop, co)),
                new DialogService.ButtonSpec(messages.get("coowner.member.remove"),
                        () -> confirmRemove(primary, shop, co))
        ));
    }

    private void showEditMember(Player primary, Shop shop, CoOwner co) {
        dialogs.input(primary,
                        messages.get("coowner.edit.title"),
                        messages.get("coowner.edit.body"),
                        messages.get("coowner.edit.submit"))
                .dropdown("role", messages.get("coowner.add.role-label"),
                        List.of(
                                new DialogService.InputBuilder.Option("MANAGER", Component.text("MANAGER")),
                                new DialogService.InputBuilder.Option("STAFF", Component.text("STAFF"))
                        ), co.role() == CoOwnerRole.STAFF ? 1 : 0)
                .text("share", messages.get("coowner.add.share-label"), co.share().toPlainString())
                .onSubmit(response -> {
                    CoOwnerRole role = CoOwnerRole.valueOf(response.getDropdownOptionId("role"));
                    BigDecimal share = role == CoOwnerRole.STAFF
                            ? BigDecimal.ZERO : parseShare(response.getText("share"));
                    co.setRole(role);
                    co.setShare(share);
                    persistAndRebalance(primary, shop, co, /*remove*/ false);
                });
    }

    private void confirmRemove(Player primary, Shop shop, CoOwner co) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(co.playerUuid());
        Component body = messages.get("coowner.remove.body",
                Placeholder.parsed("player", p.getName() == null ? co.playerUuid().toString() : p.getName()));
        dialogs.confirmOnce(primary,
                messages.get("coowner.remove.title"), body,
                messages.get("coowner.remove.yes"), messages.get("coowner.remove.no"),
                () -> persistAndRebalance(primary, shop, co, /*remove*/ true),
                () -> {});
    }

    private void persistAndRebalance(Player primary, Shop shop, CoOwner change, boolean remove) {
        try {
            List<CoOwner> current = storage.coOwners().findByShop(shop.id());
            if (remove) {
                current.removeIf(c -> c.playerUuid().equals(change.playerUuid()));
                storage.coOwners().delete(shop.id(), change.playerUuid());
            } else {
                boolean found = false;
                for (CoOwner co : current) {
                    if (co.playerUuid().equals(change.playerUuid())) {
                        co.setRole(change.role());
                        co.setShare(change.share());
                        found = true;
                        break;
                    }
                }
                if (!found) current.add(change);
                storage.coOwners().upsert(change);
            }

            // PRIMARY absorbs any rounding so shares sum to exactly 100.00.
            BigDecimal sum = BigDecimal.ZERO;
            CoOwner primaryEntry = null;
            for (CoOwner co : current) {
                if (co.role() == CoOwnerRole.PRIMARY) primaryEntry = co;
                if (co.role() != CoOwnerRole.STAFF) sum = sum.add(co.share());
            }
            if (primaryEntry != null) {
                BigDecimal diff = HUNDRED.subtract(sum);
                if (diff.signum() != 0) {
                    primaryEntry.setShare(primaryEntry.share().add(diff));
                    storage.coOwners().upsert(primaryEntry);
                }
            }
            primary.sendMessage(messages.get(remove ? "coowner.remove.done" : "coowner.add.done"));
            showList(primary, shop, current);
        } catch (SQLException ex) {
            LOG.severe("Co-owner persist failed: " + ex.getMessage());
            primary.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
        }
    }

    // ---- transfer ----

    private void performTransfer(Player oldPrimary, Shop shop, OfflinePlayer newPrimary) {
        try {
            UUID newId = newPrimary.getUniqueId();
            List<CoOwner> current = storage.coOwners().findByShop(shop.id());

            // Demote current PRIMARY to MANAGER (we don't auto-evict; spec lets PRIMARY choose).
            CoOwner oldEntry = null;
            CoOwner newEntry = null;
            for (CoOwner co : current) {
                if (co.role() == CoOwnerRole.PRIMARY) oldEntry = co;
                if (co.playerUuid().equals(newId)) newEntry = co;
            }
            if (oldEntry == null) {
                oldPrimary.sendMessage(messages.get("error.generic",
                        Placeholder.parsed("reason", "no current primary")));
                return;
            }
            // Demote old primary to MANAGER (keeps existing share)
            oldEntry.setRole(CoOwnerRole.MANAGER);
            storage.coOwners().upsert(oldEntry);

            if (newEntry == null) {
                newEntry = new CoOwner(shop.id(), newId, CoOwnerRole.PRIMARY,
                        BigDecimal.ZERO, Instant.now(), oldPrimary.getUniqueId());
            } else {
                newEntry.setRole(CoOwnerRole.PRIMARY);
            }
            storage.coOwners().upsert(newEntry);

            // Shop owner_uuid is the PRIMARY's cache → keep in sync.
            shop.setOwnerUuid(newId);
            shopService.update(shop);
            villagerManager.refreshDisplayName(shop);

            oldPrimary.sendMessage(messages.get("coowner.transfer.done",
                    Placeholder.parsed("player",
                            newPrimary.getName() == null ? newId.toString() : newPrimary.getName())));
        } catch (SQLException ex) {
            LOG.severe("PRIMARY transfer failed: " + ex.getMessage());
            oldPrimary.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
        }
    }

    private static BigDecimal parseShare(String raw) {
        try {
            BigDecimal v = new BigDecimal(raw.trim());
            if (v.signum() < 0) return BigDecimal.ZERO;
            if (v.compareTo(HUNDRED) > 0) return HUNDRED;
            return v.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}

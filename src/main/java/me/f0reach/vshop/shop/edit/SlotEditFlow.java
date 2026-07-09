package me.f0reach.vshop.shop.edit;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.item.ItemIdentity;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.ui.dialog.DialogService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Drives the slot create / edit / delete dialogs. The flow is
 * hub → per-side form: the hub is a MultiButtonDialog that toggles the
 * TradeSide in-place and only surfaces the sell/buy setting buttons that
 * match the currently selected side. Persistence happens on per-form submit.
 */
public final class SlotEditFlow {

    private static final Logger LOG = Logger.getLogger(SlotEditFlow.class.getName());

    private final DialogService dialogs;
    private final MessageManager messages;
    private final EconomyService economy;
    private final ShopEditService editService;
    private final PluginConfig config;

    public SlotEditFlow(DialogService dialogs, MessageManager messages, EconomyService economy,
                        ShopEditService editService, PluginConfig config) {
        this.dialogs = dialogs;
        this.messages = messages;
        this.economy = economy;
        this.editService = editService;
        this.config = config;
    }

    public void openCreate(Player editor, Shop shop, int slotIndex, ItemStack template, Runnable afterRefresh) {
        if (ItemIdentity.isBlacklisted(config, template)) {
            editor.sendMessage(messages.get("error.blacklisted-item"));
            return;
        }
        WorkingSlot working = WorkingSlot.forCreate(config);
        showHub(editor, shop, /*existing*/ null, slotIndex, template, working, afterRefresh);
    }

    public void openEdit(Player editor, Shop shop, ShopSlot existing, Runnable afterRefresh) {
        WorkingSlot working = WorkingSlot.from(existing);
        showHub(editor, shop, existing, existing.slotIndex(),
                ItemIdentity.copyTemplate(existing.itemTemplate()), working, afterRefresh);
    }

    public void openDelete(Player editor, Shop shop, ShopSlot slot, Runnable afterRefresh) {
        confirmDelete(editor, slot, afterRefresh, () -> {});
    }

    private void showHub(Player editor, Shop shop, ShopSlot existing, int slotIndex,
                         ItemStack template, WorkingSlot working, Runnable afterRefresh) {
        Component title = messages.get(existing == null
                ? "edit.slot.hub.create-title" : "edit.slot.hub.edit-title");
        Component body = messages.get("edit.slot.hub.body",
                Placeholder.parsed("item", template.getType().name()),
                Placeholder.component("side", sideLabel(working.side)));

        List<DialogService.ButtonSpec> buttons = new ArrayList<>();

        buttons.add(new DialogService.ButtonSpec(
                messages.get("edit.slot.hub.cycle-side",
                        Placeholder.component("side", sideLabel(working.side))),
                () -> {
                    working.side = cycleSide(working.side);
                    showHub(editor, shop, existing, slotIndex, template, working, afterRefresh);
                }));

        if (working.side == TradeSide.SELL || working.side == TradeSide.BOTH) {
            buttons.add(new DialogService.ButtonSpec(
                    messages.get("edit.slot.hub.sell-button"),
                    () -> showSellForm(editor, shop, existing, slotIndex, template, working, afterRefresh)));
        }
        if (working.side == TradeSide.BUY || working.side == TradeSide.BOTH) {
            buttons.add(new DialogService.ButtonSpec(
                    messages.get("edit.slot.hub.buy-button"),
                    () -> showBuyForm(editor, shop, existing, slotIndex, template, working, afterRefresh)));
        }
        buttons.add(new DialogService.ButtonSpec(
                messages.get("edit.slot.hub.save"),
                () -> commitAndClose(editor, shop, existing, slotIndex, template, working, afterRefresh)));
        buttons.add(new DialogService.ButtonSpec(
                messages.get("edit.slot.hub.cancel"),
                () -> { if (afterRefresh != null) afterRefresh.run(); }));
        if (existing != null) {
            buttons.add(new DialogService.ButtonSpec(
                    messages.get("edit.slot.hub.delete"),
                    () -> confirmDelete(editor, existing, afterRefresh,
                            () -> showHub(editor, shop, existing, slotIndex, template, working, afterRefresh))));
        }

        dialogs.multiButton(editor, title, body, buttons);
    }

    private void commitAndClose(Player editor, Shop shop, ShopSlot existing, int slotIndex,
                                ItemStack template, WorkingSlot working, Runnable afterRefresh) {
        try {
            persistWorking(existing, shop, slotIndex, template, working);
            editor.sendMessage(messages.get(existing == null ? "edit.slot.created" : "edit.slot.updated"));
            if (afterRefresh != null) afterRefresh.run();
        } catch (SQLException ex) {
            LOG.severe("Slot persist failed: " + ex.getMessage());
            editor.sendMessage(messages.get("error.generic",
                    Placeholder.parsed("reason", ex.getMessage())));
            showHub(editor, shop, existing, slotIndex, template, working, afterRefresh);
        }
    }

    private void showSellForm(Player editor, Shop shop, ShopSlot existing, int slotIndex,
                              ItemStack template, WorkingSlot working, Runnable afterRefresh) {
        int unitMax = Math.max(1, template.getMaxStackSize());
        Component title = messages.get("edit.slot.sell-title");
        Component body = messages.get("edit.slot.body",
                Placeholder.parsed("item", template.getType().name()),
                Placeholder.parsed("unit_max", Integer.toString(unitMax)));
        Component submit = messages.get("edit.slot.submit");

        boolean allowCommand = shop.isAdminShop();

        var builder = dialogs.input(editor, title, body, submit)
                .text("unitPrice", messages.get("edit.slot.unit-price"),
                        working.unitPrice.toPlainString())
                .text("unitAmount", messages.get("edit.slot.unit-amount",
                        Placeholder.parsed("max", Integer.toString(unitMax))),
                        Integer.toString(working.unitAmount))
                .text("tradeLimit", messages.get("edit.slot.trade-limit"),
                        working.tradeLimit == null ? "" : working.tradeLimit.toString())
                .dropdown("limitScope", messages.get("edit.slot.limit-scope"),
                        limitScopeOptions(), working.limitScope == LimitScope.GLOBAL ? 1 : 0)
                .text("resetSeconds", messages.get("edit.slot.reset-seconds"),
                        working.resetPeriod == null ? "" : Long.toString(working.resetPeriod.toSeconds()));
        if (allowCommand) {
            builder = builder.text("command", messages.get("edit.slot.command"),
                    working.command == null ? "" : working.command);
        }

        Runnable reopenHub = () -> showHub(editor, shop, existing, slotIndex, template, working, afterRefresh);

        builder.onSubmit(response -> {
            try {
                BigDecimal unitPrice = parsePositive(response.getText("unitPrice"));
                int unitAmount = parsePositiveInt(response.getText("unitAmount"));
                Integer tradeLimit = optionalInt(response.getText("tradeLimit"));
                LimitScope scope = LimitScope.valueOf(response.getDropdownOptionId("limitScope"));
                Duration resetPeriod = optionalSeconds(response.getText("resetSeconds"));
                // Command field only surfaced for admin shops; ignore/clear
                // otherwise so a shop swapped from admin -> player through
                // some other flow doesn't retain a stale command string.
                String command = allowCommand ? optionalString(response.getText("command")) : null;

                if (unitPrice.compareTo(config.economy().priceMin()) < 0
                        || unitPrice.compareTo(config.economy().priceMax()) > 0) {
                    editor.sendMessage(messages.get("error.invalid-price"));
                    reopenHub.run();
                    return;
                }
                if (unitAmount <= 0 || unitAmount > unitMax) {
                    editor.sendMessage(messages.get("error.unit-amount-too-large",
                            Placeholder.parsed("max", Integer.toString(unitMax))));
                    reopenHub.run();
                    return;
                }

                working.unitPrice = unitPrice;
                working.unitAmount = unitAmount;
                working.tradeLimit = tradeLimit;
                working.limitScope = scope;
                working.resetPeriod = resetPeriod;
                if (allowCommand) working.command = command;
                reopenHub.run();
            } catch (NumberFormatException nfe) {
                editor.sendMessage(messages.get("error.invalid-amount"));
                reopenHub.run();
            } catch (IllegalArgumentException iae) {
                editor.sendMessage(messages.get("error.generic",
                        Placeholder.parsed("reason", iae.getMessage())));
                reopenHub.run();
            }
        });
    }

    private void showBuyForm(Player editor, Shop shop, ShopSlot existing, int slotIndex,
                             ItemStack template, WorkingSlot working, Runnable afterRefresh) {
        int unitMax = Math.max(1, template.getMaxStackSize());
        Component title = messages.get("edit.slot.buy-title");
        Component body = messages.get("edit.slot.body",
                Placeholder.parsed("item", template.getType().name()),
                Placeholder.parsed("unit_max", Integer.toString(unitMax)));
        Component submit = messages.get("edit.slot.submit");

        var builder = dialogs.input(editor, title, body, submit)
                .text("buyUnitPrice", messages.get("edit.slot.buy-unit-price"),
                        working.buyUnitPrice.toPlainString())
                .text("buyCapacity", messages.get("edit.slot.buy-capacity"),
                        Integer.toString(working.buyCapacity))
                .text("unitAmount", messages.get("edit.slot.unit-amount",
                        Placeholder.parsed("max", Integer.toString(unitMax))),
                        Integer.toString(working.unitAmount))
                .text("tradeLimit", messages.get("edit.slot.trade-limit"),
                        working.tradeLimit == null ? "" : working.tradeLimit.toString())
                .dropdown("limitScope", messages.get("edit.slot.limit-scope"),
                        limitScopeOptions(), working.limitScope == LimitScope.GLOBAL ? 1 : 0)
                .text("resetSeconds", messages.get("edit.slot.reset-seconds"),
                        working.resetPeriod == null ? "" : Long.toString(working.resetPeriod.toSeconds()));

        Runnable reopenHub = () -> showHub(editor, shop, existing, slotIndex, template, working, afterRefresh);

        builder.onSubmit(response -> {
            try {
                BigDecimal buyUnitPrice = parsePositive(response.getText("buyUnitPrice"));
                int buyCapacity = parseBuyCapacity(response.getText("buyCapacity"));
                int unitAmount = parsePositiveInt(response.getText("unitAmount"));
                Integer tradeLimit = optionalInt(response.getText("tradeLimit"));
                LimitScope scope = LimitScope.valueOf(response.getDropdownOptionId("limitScope"));
                Duration resetPeriod = optionalSeconds(response.getText("resetSeconds"));

                if (buyUnitPrice.compareTo(config.economy().priceMin()) < 0
                        || buyUnitPrice.compareTo(config.economy().priceMax()) > 0) {
                    editor.sendMessage(messages.get("error.invalid-price"));
                    reopenHub.run();
                    return;
                }
                if (unitAmount <= 0 || unitAmount > unitMax) {
                    editor.sendMessage(messages.get("error.unit-amount-too-large",
                            Placeholder.parsed("max", Integer.toString(unitMax))));
                    reopenHub.run();
                    return;
                }

                working.buyUnitPrice = buyUnitPrice;
                working.buyCapacity = buyCapacity;
                working.unitAmount = unitAmount;
                working.tradeLimit = tradeLimit;
                working.limitScope = scope;
                working.resetPeriod = resetPeriod;
                reopenHub.run();
            } catch (NumberFormatException nfe) {
                editor.sendMessage(messages.get("error.invalid-amount"));
                reopenHub.run();
            } catch (IllegalArgumentException iae) {
                editor.sendMessage(messages.get("error.generic",
                        Placeholder.parsed("reason", iae.getMessage())));
                reopenHub.run();
            }
        });
    }

    private void confirmDelete(Player editor, ShopSlot slot, Runnable onDone, Runnable onCancel) {
        dialogs.confirmOnce(editor,
                messages.get("edit.delete.title"),
                messages.get("edit.delete.body",
                        Placeholder.parsed("item", slot.itemTemplate().getType().name())),
                messages.get("edit.delete.yes"),
                messages.get("edit.delete.no"),
                () -> {
                    try {
                        editService.deleteSlot(slot);
                        editor.sendMessage(messages.get("edit.delete.done"));
                        if (onDone != null) onDone.run();
                    } catch (SQLException ex) {
                        LOG.severe("Failed to delete slot: " + ex.getMessage());
                        editor.sendMessage(messages.get("error.generic",
                                Placeholder.parsed("reason", ex.getMessage())));
                    }
                }, onCancel);
    }

    /**
     * Merge {@code working} into a persistable {@link ShopSlot} and save it.
     * SELL-only slots persist buy-side fields as inert defaults (null price, 0 capacity)
     * so the trade path treats them as "buy is closed".
     */
    private void persistWorking(ShopSlot existing, Shop shop, int slotIndex,
                                ItemStack template, WorkingSlot working) throws SQLException {
        BigDecimal buyUnitPrice = working.side == TradeSide.SELL ? null : working.buyUnitPrice;
        int buyCapacity = working.side == TradeSide.SELL ? 0 : working.buyCapacity;
        // Command sale is admin-only and SELL-only. Silently drop the command
        // on any other shape so the DB never holds an unreachable value.
        String command = (shop.isAdminShop()
                && (working.side == TradeSide.SELL || working.side == TradeSide.BOTH))
                ? emptyToNull(working.command) : null;
        ShopSlot slot;
        if (existing == null) {
            slot = new ShopSlot(UUID.randomUUID(), shop.id(), slotIndex, working.side,
                    ItemIdentity.copyTemplate(template),
                    working.unitPrice, buyUnitPrice, working.unitAmount, buyCapacity,
                    working.tradeLimit, working.limitScope, working.resetPeriod, command);
        } else {
            existing.setSide(working.side);
            existing.setItemTemplate(ItemIdentity.copyTemplate(template));
            existing.setUnitPrice(working.unitPrice);
            existing.setBuyUnitPrice(buyUnitPrice);
            existing.setUnitAmount(working.unitAmount);
            existing.setBuyCapacity(buyCapacity);
            existing.setTradeLimit(working.tradeLimit);
            existing.setLimitScope(working.limitScope);
            existing.setResetPeriod(working.resetPeriod);
            existing.setCommand(command);
            slot = existing;
        }
        editService.persistSlot(slot);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private List<DialogService.InputBuilder.Option> limitScopeOptions() {
        return List.of(
                new DialogService.InputBuilder.Option("PER_PLAYER",
                        messages.get("edit.slot.scope.per-player")),
                new DialogService.InputBuilder.Option("GLOBAL",
                        messages.get("edit.slot.scope.global")));
    }

    private Component sideLabel(TradeSide side) {
        String key = switch (side) {
            case SELL -> "edit.slot.side-sell";
            case BUY -> "edit.slot.side-buy";
            case BOTH -> "edit.slot.side-both";
        };
        return messages.get(key);
    }

    private static TradeSide cycleSide(TradeSide s) {
        return switch (s) {
            case SELL -> TradeSide.BUY;
            case BUY -> TradeSide.BOTH;
            case BOTH -> TradeSide.SELL;
        };
    }

    private static BigDecimal parsePositive(String raw) {
        BigDecimal v = new BigDecimal(raw.trim());
        if (v.signum() <= 0) throw new NumberFormatException("must be positive");
        return v;
    }

    private static int parsePositiveInt(String raw) {
        int v = Integer.parseInt(raw.trim());
        if (v <= 0) throw new NumberFormatException("must be positive");
        return v;
    }

    /**
     * Buy-side capacity input: blank → 0 (no capacity), -1 → unlimited sentinel,
     * any other negative is clamped to -1 so partial typos can't sneak through.
     */
    private static int parseBuyCapacity(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        int v = Integer.parseInt(raw.trim());
        return v < 0 ? -1 : v;
    }

    private static Integer optionalInt(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Integer.parseInt(raw.trim());
    }

    private static String optionalString(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Duration optionalSeconds(String raw) {
        if (raw == null || raw.isBlank()) return null;
        long s = Long.parseLong(raw.trim());
        if (s <= 0) return null;
        return Duration.ofSeconds(s);
    }

    /** Used by edit-listener to thread a "refresh chest UI after save" callback. */
    public interface RefreshCallback extends Consumer<Player> {}

    /**
     * Ephemeral in-memory holder shared between the hub and the two per-side
     * forms. Side toggles and form submissions mutate this so we don't need to
     * touch the database until the user commits a form.
     */
    private static final class WorkingSlot {
        TradeSide side;
        BigDecimal unitPrice;
        BigDecimal buyUnitPrice;
        int unitAmount;
        int buyCapacity;
        Integer tradeLimit;
        LimitScope limitScope;
        Duration resetPeriod;
        String command;

        static WorkingSlot forCreate(PluginConfig config) {
            WorkingSlot w = new WorkingSlot();
            w.side = TradeSide.SELL;
            w.unitPrice = new BigDecimal("100");
            w.buyUnitPrice = new BigDecimal("100");
            w.unitAmount = 1;
            w.buyCapacity = 0;
            w.tradeLimit = null;
            w.limitScope = LimitScope.valueOf(config.shop().defaultLimitScope().name());
            w.resetPeriod = null;
            w.command = null;
            return w;
        }

        static WorkingSlot from(ShopSlot existing) {
            WorkingSlot w = new WorkingSlot();
            w.side = existing.side();
            w.unitPrice = existing.unitPrice();
            w.buyUnitPrice = existing.buyUnitPrice() != null
                    ? existing.buyUnitPrice() : existing.unitPrice();
            w.unitAmount = existing.unitAmount();
            w.buyCapacity = existing.buyCapacity();
            w.tradeLimit = existing.tradeLimit();
            w.limitScope = existing.limitScope();
            w.resetPeriod = existing.resetPeriod();
            w.command = existing.command();
            return w;
        }
    }
}

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
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Drives the slot create / edit / delete dialogs. The current design uses a
 * single InputDialog with several text fields rather than a step-by-step flow
 * — fewer round-trips, and the dropdown / boolean inputs already cover the
 * "side" pick inline.
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
        showSlotForm(editor, shop, /*existing*/ null, slotIndex, template, afterRefresh);
    }

    public void openEdit(Player editor, Shop shop, ShopSlot existing, Runnable afterRefresh) {
        showSlotForm(editor, shop, existing, existing.slotIndex(),
                ItemIdentity.copyTemplate(existing.itemTemplate()), afterRefresh);
    }

    public void openDelete(Player editor, Shop shop, ShopSlot slot, Runnable afterRefresh) {
        dialogs.confirmOnce(editor,
                messages.get("edit.delete.title"),
                messages.get("edit.delete.body", Placeholder.parsed("item", slot.itemTemplate().getType().name())),
                messages.get("edit.delete.yes"),
                messages.get("edit.delete.no"),
                () -> {
                    try {
                        editService.deleteSlot(slot);
                        editor.sendMessage(messages.get("edit.delete.done"));
                        if (afterRefresh != null) afterRefresh.run();
                    } catch (SQLException ex) {
                        LOG.severe("Failed to delete slot: " + ex.getMessage());
                        editor.sendMessage(messages.get("error.generic",
                                Placeholder.parsed("reason", ex.getMessage())));
                    }
                }, () -> {});
    }

    private void showSlotForm(Player editor, Shop shop, ShopSlot existing, int slotIndex,
                              ItemStack template, Runnable afterRefresh) {
        int unitMax = Math.max(1, template.getMaxStackSize());
        Component title = messages.get(existing == null ? "edit.slot.create-title" : "edit.slot.edit-title");
        Component body = messages.get("edit.slot.body",
                Placeholder.parsed("item", template.getType().name()),
                Placeholder.parsed("unit_max", Integer.toString(unitMax)));
        Component submit = messages.get("edit.slot.submit");

        String defaultSide = existing == null ? "SELL" : existing.side().name();
        String defaultUnitPrice = existing == null ? "100" : existing.unitPrice().toPlainString();
        String defaultUnitAmount = existing == null ? "1" : Integer.toString(existing.unitAmount());
        String defaultBuyUnitPrice = (existing == null || existing.buyUnitPrice() == null)
                ? defaultUnitPrice : existing.buyUnitPrice().toPlainString();
        String defaultBuyCapacity = existing == null ? "0" : Integer.toString(existing.buyCapacity());
        String defaultLimit = (existing == null || existing.tradeLimit() == null)
                ? "" : existing.tradeLimit().toString();
        String defaultScope = existing == null
                ? config.shop().defaultLimitScope().name() : existing.limitScope().name();
        String defaultReset = (existing == null || existing.resetPeriod() == null)
                ? "" : Long.toString(existing.resetPeriod().toSeconds());

        var builder = dialogs.input(editor, title, body, submit)
                .dropdown("side", messages.get("edit.slot.side"),
                        List.of(
                                new DialogService.InputBuilder.Option("SELL", Component.text("販売 SELL")),
                                new DialogService.InputBuilder.Option("BUY", Component.text("買取 BUY")),
                                new DialogService.InputBuilder.Option("BOTH", Component.text("双方向 BOTH"))
                        ), indexOfSide(defaultSide))
                .text("unitPrice", messages.get("edit.slot.unit-price"), defaultUnitPrice)
                .text("unitAmount", messages.get("edit.slot.unit-amount",
                        Placeholder.parsed("max", Integer.toString(unitMax))), defaultUnitAmount)
                .text("buyUnitPrice", messages.get("edit.slot.buy-unit-price"), defaultBuyUnitPrice)
                .text("buyCapacity", messages.get("edit.slot.buy-capacity"), defaultBuyCapacity)
                .text("tradeLimit", messages.get("edit.slot.trade-limit"), defaultLimit)
                .dropdown("limitScope", messages.get("edit.slot.limit-scope"),
                        List.of(
                                new DialogService.InputBuilder.Option("PER_PLAYER", Component.text("プレイヤー単位")),
                                new DialogService.InputBuilder.Option("GLOBAL", Component.text("全体共有"))
                        ), defaultScope.equals("GLOBAL") ? 1 : 0)
                .text("resetSeconds", messages.get("edit.slot.reset-seconds"), defaultReset);

        builder.onSubmit(response -> {
            try {
                TradeSide side = TradeSide.valueOf(response.getDropdownOptionId("side"));
                BigDecimal unitPrice = parsePositive(response.getText("unitPrice"));
                int unitAmount = parsePositiveInt(response.getText("unitAmount"));
                BigDecimal buyUnitPrice = side != TradeSide.SELL
                        ? parsePositive(response.getText("buyUnitPrice")) : null;
                int buyCapacity = side != TradeSide.SELL
                        ? parseBuyCapacity(response.getText("buyCapacity")) : 0;
                Integer tradeLimit = optionalInt(response.getText("tradeLimit"));
                LimitScope scope = LimitScope.valueOf(response.getDropdownOptionId("limitScope"));
                Duration resetPeriod = optionalSeconds(response.getText("resetSeconds"));

                if (unitPrice.compareTo(config.economy().priceMin()) < 0
                        || unitPrice.compareTo(config.economy().priceMax()) > 0) {
                    editor.sendMessage(messages.get("error.invalid-price"));
                    return;
                }
                if (unitAmount <= 0 || unitAmount > unitMax) {
                    editor.sendMessage(messages.get("error.unit-amount-too-large",
                            Placeholder.parsed("max", Integer.toString(unitMax))));
                    return;
                }

                ShopSlot slot;
                if (existing == null) {
                    slot = new ShopSlot(UUID.randomUUID(), shop.id(), slotIndex, side,
                            ItemIdentity.copyTemplate(template),
                            unitPrice, buyUnitPrice, unitAmount, buyCapacity,
                            tradeLimit, scope, resetPeriod);
                } else {
                    existing.setSide(side);
                    existing.setItemTemplate(ItemIdentity.copyTemplate(template));
                    existing.setUnitPrice(unitPrice);
                    existing.setBuyUnitPrice(buyUnitPrice);
                    existing.setUnitAmount(unitAmount);
                    existing.setBuyCapacity(buyCapacity);
                    existing.setTradeLimit(tradeLimit);
                    existing.setLimitScope(scope);
                    existing.setResetPeriod(resetPeriod);
                    slot = existing;
                }
                editService.persistSlot(slot);
                editor.sendMessage(messages.get(existing == null ? "edit.slot.created" : "edit.slot.updated"));
                if (afterRefresh != null) afterRefresh.run();
            } catch (NumberFormatException nfe) {
                editor.sendMessage(messages.get("error.invalid-amount"));
            } catch (IllegalArgumentException iae) {
                editor.sendMessage(messages.get("error.generic",
                        Placeholder.parsed("reason", iae.getMessage())));
            } catch (SQLException ex) {
                LOG.severe("Slot persist failed: " + ex.getMessage());
                editor.sendMessage(messages.get("error.generic",
                        Placeholder.parsed("reason", ex.getMessage())));
            }
        });
    }

    private static int indexOfSide(String s) {
        return switch (s) {
            case "BUY" -> 1;
            case "BOTH" -> 2;
            default -> 0;
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

    private static int parseInt(String raw) {
        if (raw == null || raw.isBlank()) return 0;
        return Integer.parseInt(raw.trim());
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

    private static Duration optionalSeconds(String raw) {
        if (raw == null || raw.isBlank()) return null;
        long s = Long.parseLong(raw.trim());
        if (s <= 0) return null;
        return Duration.ofSeconds(s);
    }

    /** Used by edit-listener to thread a "refresh chest UI after save" callback. */
    public interface RefreshCallback extends Consumer<Player> {}
}

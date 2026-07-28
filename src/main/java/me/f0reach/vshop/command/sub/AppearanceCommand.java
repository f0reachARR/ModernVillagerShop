package me.f0reach.vshop.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.f0reach.vshop.command.CommandSupport;
import me.f0reach.vshop.command.ShopIdSuggestions;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.model.ShopEntityKind;
import me.f0reach.vshop.model.SkinVariant;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * {@code /vshop appearance} — command-only control over how a shop is rendered.
 * There is deliberately no Dialog UI for this: it is an occasional,
 * many-knobbed operation that reads better as a flat command surface.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AppearanceCommand {

    /**
     * FancyNpcs' equipment slots. Kept as strings rather than mirroring its enum
     * — the set is version-dependent and belongs to that plugin, so these are
     * only completions and the backend does the real validation.
     */
    private static final List<String> EQUIPMENT_SLOTS =
            List.of("MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET", "BODY", "SADDLE");

    /** Clears a skin/attribute rather than setting one. */
    private static final String CLEAR_TOKEN = "@none";

    private final CommandSupport support;
    private final ShopIdSuggestions shopIds;

    public AppearanceCommand(CommandSupport support) {
        this.support = support;
        this.shopIds = new ShopIdSuggestions(support);
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("appearance")
                .requires(s -> s.getSender().hasPermission("modernvillagershop.edit.appearance")
                        || s.getSender().hasPermission("modernvillagershop.edit.others")
                        || s.getSender().hasPermission("modernvillagershop.admin.appearance"))
                .then(Commands.argument("shopId", StringArgumentType.word())
                        .suggests(shopIds.provider())
                        .then(Commands.literal("show")
                                .executes(ctx -> show(ctx, shopId(ctx))))
                        .then(Commands.literal("npc")
                                .executes(ctx -> toNpc(ctx, shopId(ctx), null))
                                .then(Commands.argument("skin", StringArgumentType.word())
                                        .suggests(onlinePlayers())
                                        .executes(ctx -> toNpc(ctx, shopId(ctx),
                                                StringArgumentType.getString(ctx, "skin")))))
                        .then(Commands.literal("villager")
                                .executes(ctx -> toVillager(ctx, shopId(ctx))))
                        .then(Commands.literal("type")
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests(entityTypes())
                                        .executes(ctx -> setType(ctx, shopId(ctx),
                                                StringArgumentType.getString(ctx, "type")))))
                        .then(Commands.literal("skin")
                                .then(Commands.argument("skin", StringArgumentType.string())
                                        .suggests(onlinePlayers())
                                        .executes(ctx -> setSkin(ctx, shopId(ctx),
                                                StringArgumentType.getString(ctx, "skin"), false))
                                        .then(Commands.literal("slim")
                                                .executes(ctx -> setSkin(ctx, shopId(ctx),
                                                        StringArgumentType.getString(ctx, "skin"), true)))))
                        .then(Commands.literal("glow")
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ctx -> setGlow(ctx, shopId(ctx),
                                                BoolArgumentType.getBool(ctx, "enabled"), null))
                                        .then(Commands.argument("color", StringArgumentType.word())
                                                .suggests(colors())
                                                .executes(ctx -> setGlow(ctx, shopId(ctx),
                                                        BoolArgumentType.getBool(ctx, "enabled"),
                                                        StringArgumentType.getString(ctx, "color"))))))
                        .then(Commands.literal("scale")
                                .then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.05, 10.0))
                                        .executes(ctx -> setScale(ctx, shopId(ctx),
                                                (float) DoubleArgumentType.getDouble(ctx, "scale")))))
                        .then(Commands.literal("equip")
                                .then(Commands.argument("slot", StringArgumentType.word())
                                        .suggests(equipmentSlots())
                                        .executes(ctx -> equip(ctx, shopId(ctx),
                                                StringArgumentType.getString(ctx, "slot"), false))
                                        .then(Commands.literal("none")
                                                .executes(ctx -> equip(ctx, shopId(ctx),
                                                        StringArgumentType.getString(ctx, "slot"), true)))))
                        .then(Commands.literal("attribute")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(ctx -> setAttribute(ctx, shopId(ctx),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "value"))))))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx, shopId(ctx)))));
    }

    // ---- subcommands ----

    /** Read-only, so the console may run it too — it has no shop role to check. */
    private int show(CommandContext<CommandSourceStack> ctx, String shopIdPrefix) {
        var sender = ctx.getSource().getSender();
        Shop shop = support.findShopByPrefix(shopIdPrefix);
        if (shop == null) {
            support.sendShopNotFound(sender, shopIdPrefix);
            return 0;
        }
        if (sender instanceof Player player) {
            try {
                if (!support.plugin().editService().canEdit(player, shop)) {
                    player.sendMessage(support.messages().get("shop.edit.no-permission"));
                    return 0;
                }
            } catch (SQLException ex) {
                support.sendGenericError(player, ex);
                return 0;
            }
        }
        new me.f0reach.vshop.ui.text.AppearanceView(support.messages(), support.enumLabels())
                .send(sender, shop, support.plugin().shopAppearanceService().current(shop));
        return Command.SINGLE_SUCCESS;
    }

    private int toNpc(CommandContext<CommandSourceStack> ctx, String shopIdPrefix, String skin) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        if (!requireIntegration(target)) return 0;
        if (skin != null && !allowsSkinSource(target.player(), skin)) return 0;

        String resolvedSkin = skin != null ? skin : defaultSkinFor(target.shop(), target.player());
        return mutate(target, a -> {
            a.setBackend(ShopEntityKind.FANCY_NPC);
            a.setEntityType(EntityType.PLAYER);
            a.setSkin(resolvedSkin);
        }, "command.appearance.npc-done",
                Placeholder.parsed("shop_name", target.shop().name()));
    }

    private int toVillager(CommandContext<CommandSourceStack> ctx, String shopIdPrefix) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        return mutate(target, a -> a.setBackend(ShopEntityKind.VILLAGER),
                "command.appearance.villager-done",
                Placeholder.parsed("shop_name", target.shop().name()));
    }

    private int setType(CommandContext<CommandSourceStack> ctx, String shopIdPrefix, String raw) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        if (!requireIntegration(target)) return 0;

        EntityType type = parseEntityType(raw);
        if (type == null) {
            target.player().sendMessage(support.messages().get("command.appearance.invalid-type",
                    Placeholder.parsed("type", raw)));
            return 0;
        }
        if (!fancyNpcs().allowsType(type)
                && !target.player().hasPermission("modernvillagershop.admin.appearance")) {
            target.player().sendMessage(support.messages().get("command.appearance.type-not-allowed",
                    Placeholder.parsed("type", type.name())));
            return 0;
        }
        return mutate(target, a -> a.setEntityType(type), "command.appearance.updated");
    }

    private int setSkin(CommandContext<CommandSourceStack> ctx, String shopIdPrefix,
                        String skin, boolean slim) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        if (!requireIntegration(target)) return 0;

        if (CLEAR_TOKEN.equalsIgnoreCase(skin)) {
            return mutate(target, a -> {
                a.setSkin(null);
                a.setSkinVariant(null);
            }, "command.appearance.updated");
        }
        if (!allowsSkinSource(target.player(), skin)) return 0;

        ShopAppearance current = support.plugin().shopAppearanceService().current(target.shop());
        EntityType type = current.entityType() == null ? EntityType.PLAYER : current.entityType();
        if (type != EntityType.PLAYER) {
            target.player().sendMessage(support.messages().get("command.appearance.skin-needs-player"));
            return 0;
        }
        return mutate(target, a -> {
            a.setSkin(skin);
            a.setSkinVariant(slim ? SkinVariant.SLIM : SkinVariant.AUTO);
        }, "command.appearance.updated");
    }

    private int setGlow(CommandContext<CommandSourceStack> ctx, String shopIdPrefix,
                        boolean enabled, String colorName) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;

        NamedTextColor color = null;
        if (colorName != null) {
            color = NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT));
            if (color == null) {
                target.player().sendMessage(support.messages().get("command.appearance.invalid-color",
                        Placeholder.parsed("color", colorName)));
                return 0;
            }
        }
        NamedTextColor chosen = color;
        return mutate(target, a -> {
            a.setGlowing(enabled);
            if (chosen != null) a.setGlowColor(chosen);
            if (!enabled) a.setGlowColor(null);
        }, "command.appearance.updated");
    }

    private int setScale(CommandContext<CommandSourceStack> ctx, String shopIdPrefix, float scale) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;

        float max = fancyNpcs().maxScale();
        boolean unrestricted = target.player().hasPermission("modernvillagershop.admin.appearance");
        if (!unrestricted && (scale <= 0 || scale > max)) {
            target.player().sendMessage(support.messages().get("command.appearance.invalid-scale",
                    Placeholder.parsed("min", "0.05"),
                    Placeholder.parsed("max", trim(max))));
            return 0;
        }
        return mutate(target, a -> a.setScale(scale), "command.appearance.updated");
    }

    private int equip(CommandContext<CommandSourceStack> ctx, String shopIdPrefix,
                      String rawSlot, boolean clear) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        if (!requireIntegration(target)) return 0;

        String slot = rawSlot.toUpperCase(Locale.ROOT);
        if (!EQUIPMENT_SLOTS.contains(slot)) {
            target.player().sendMessage(support.messages().get("command.appearance.invalid-slot",
                    Placeholder.parsed("slot", rawSlot)));
            return 0;
        }
        if (clear) {
            return mutate(target, a -> a.equipment().remove(slot),
                    "command.appearance.equip-cleared", Placeholder.parsed("slot", slot));
        }

        ItemStack held = target.player().getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            target.player().sendMessage(support.messages().get("command.appearance.equip-empty-hand"));
            return 0;
        }
        ItemStack copy = held.clone();
        copy.setAmount(1);
        return mutate(target, a -> a.equipment().put(slot, copy),
                "command.appearance.equip-done",
                Placeholder.parsed("slot", slot),
                Placeholder.component("item", me.f0reach.vshop.ui.text.Displays.item(copy)));
    }

    private int setAttribute(CommandContext<CommandSourceStack> ctx, String shopIdPrefix,
                             String name, String value) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        if (!requireIntegration(target)) return 0;

        if (CLEAR_TOKEN.equalsIgnoreCase(value)) {
            return mutate(target, a -> a.attributes().remove(name),
                    "command.appearance.attribute-cleared", Placeholder.parsed("name", name));
        }
        // Attribute names and values belong to FancyNpcs and vary by entity type,
        // so they are stored as given; the backend warns and skips invalid ones.
        return mutate(target, a -> a.attributes().put(name, value), "command.appearance.updated");
    }

    private int reset(CommandContext<CommandSourceStack> ctx, String shopIdPrefix) {
        Target target = resolve(ctx, shopIdPrefix);
        if (target == null) return 0;
        try {
            support.plugin().shopAppearanceService().reset(target.shop(),
                    () -> target.player().sendMessage(
                            support.messages().get("command.appearance.reset-done")));
        } catch (SQLException ex) {
            support.sendGenericError(target.player(), ex);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    // ---- shared plumbing ----

    private int mutate(Target target, Consumer<ShopAppearance> mutation, String doneKey,
                       net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        try {
            support.plugin().shopAppearanceService().apply(target.shop(), mutation,
                    () -> target.player().sendMessage(support.messages().get(doneKey, placeholders)));
        } catch (SQLException ex) {
            support.sendGenericError(target.player(), ex);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Resolves sender + shop + edit permission, reporting the failure itself. */
    private Target resolve(CommandContext<CommandSourceStack> ctx, String shopIdPrefix) {
        var sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            support.sendPlayerOnly(sender);
            return null;
        }
        Shop shop = support.findShopByPrefix(shopIdPrefix);
        if (shop == null) {
            support.sendShopNotFound(player, shopIdPrefix);
            return null;
        }
        try {
            if (!support.plugin().editService().canEdit(player, shop)) {
                player.sendMessage(support.messages().get("shop.edit.no-permission"));
                return null;
            }
        } catch (SQLException ex) {
            support.sendGenericError(player, ex);
            return null;
        }
        return new Target(player, shop);
    }

    /** NPC-only knobs are pointless without the integration; say so instead of silently storing them. */
    private boolean requireIntegration(Target target) {
        if (support.plugin().hasNpcIntegration()) return true;
        target.player().sendMessage(support.messages().get("command.appearance.unavailable"));
        return false;
    }

    /**
     * URL skins pull an arbitrary remote image through the server, so they sit
     * behind their own permission. Plain names and UUIDs go to Mojang only.
     */
    private boolean allowsSkinSource(Player player, String skin) {
        String lower = skin.toLowerCase(Locale.ROOT);
        boolean isUrl = lower.startsWith("http://") || lower.startsWith("https://");
        if (!isUrl || player.hasPermission("modernvillagershop.edit.appearance.url")) return true;
        player.sendMessage(support.messages().get("command.appearance.skin-url-no-permission"));
        return false;
    }

    /** A player shop defaults to its owner's skin; an admin shop keeps whoever ran the command. */
    private String defaultSkinFor(Shop shop, Player actor) {
        if (shop.ownerUuid() != null) {
            var cached = support.plugin().playerCacheService().findByUuid(shop.ownerUuid()).orElse(null);
            if (cached != null) return cached.name();
            var offline = org.bukkit.Bukkit.getOfflinePlayer(shop.ownerUuid());
            if (offline.getName() != null) return offline.getName();
        }
        return actor.getName();
    }

    private PluginConfig.FancyNpcsConfig fancyNpcs() {
        return support.plugin().pluginConfig().fancyNpcs();
    }

    private static String shopId(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, "shopId");
    }

    private static EntityType parseEntityType(String raw) {
        NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
        if (key != null) {
            EntityType byKey = Registry.ENTITY_TYPE.get(key);
            if (byKey != null) return byKey;
        }
        try {
            return EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String trim(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    // ---- completions ----

    private SuggestionProvider<CommandSourceStack> entityTypes() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toUpperCase(Locale.ROOT);
            PluginConfig.FancyNpcsConfig cfg = fancyNpcs();
            for (EntityType type : EntityType.values()) {
                if (!type.isSpawnable() || !cfg.allowsType(type)) continue;
                if (type.name().startsWith(prefix)) builder.suggest(type.name());
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> onlinePlayers() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            if (CLEAR_TOKEN.startsWith(prefix)) builder.suggest(CLEAR_TOKEN);
            for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(online.getName());
                }
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> colors() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>(NamedTextColor.NAMES.keys());
            for (String name : names) {
                if (name.startsWith(prefix)) builder.suggest(name);
            }
            return builder.buildFuture();
        };
    }

    private SuggestionProvider<CommandSourceStack> equipmentSlots() {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toUpperCase(Locale.ROOT);
            for (String slot : EQUIPMENT_SLOTS) {
                if (slot.startsWith(prefix)) builder.suggest(slot);
            }
            return builder.buildFuture();
        };
    }

    private record Target(Player player, Shop shop) {}
}

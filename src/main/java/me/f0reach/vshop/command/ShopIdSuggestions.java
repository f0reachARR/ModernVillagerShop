package me.f0reach.vshop.command;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.ui.text.Displays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Completions for the {@code <shopId>} command argument.
 *
 * <p>A server can hold hundreds of shops, so the full id list is noise to a
 * player standing in front of one. Only shops within {@link #NEARBY_RADIUS}
 * blocks of the sender are offered, nearest first, each carrying a tooltip with
 * name / type / owner / distance so the truncated id is identifiable.</p>
 *
 * <p>This restricts suggestions only — {@link CommandSupport#findShopByPrefix}
 * still resolves any id that is typed out, so a distant shop stays reachable
 * (and console, which has no "near", keeps seeing the whole list).</p>
 */
@SuppressWarnings("UnstableApiUsage")
public final class ShopIdSuggestions {

    /** Generous enough to cover a base, small enough to stay a local list. */
    private static final double NEARBY_RADIUS = 64.0;

    /** The client only shows ~10 rows; the cap keeps the packet small. */
    private static final int MAX_SUGGESTIONS = 20;

    private final CommandSupport support;

    public ShopIdSuggestions(CommandSupport support) {
        this.support = support;
    }

    public SuggestionProvider<CommandSourceStack> provider() {
        return (ctx, builder) -> {
            Location origin = ctx.getSource().getSender() instanceof Player player
                    ? player.getLocation()
                    : null;
            for (Candidate candidate : select(support.plugin().registry().all(),
                    builder.getRemaining().toLowerCase(Locale.ROOT), origin, MAX_SUGGESTIONS)) {
                builder.suggest(candidate.id(), tooltip(candidate));
            }
            return builder.buildFuture();
        };
    }

    /**
     * The shops to offer for {@code prefix}, nearest first and capped at
     * {@code limit}. A null {@code origin} means the sender has no position
     * (console), which drops the distance filter and orders by name instead.
     */
    static List<Candidate> select(Collection<Shop> shops, String prefix, Location origin, int limit) {
        List<Candidate> candidates = new ArrayList<>();
        for (Shop shop : shops) {
            String id = shop.id().toString().substring(0, 8);
            if (!id.startsWith(prefix)) continue;
            Double distance = origin == null ? null : distanceTo(origin, shop);
            if (origin != null && distance == null) continue; // other world / out of range
            candidates.add(new Candidate(id, shop, distance));
        }
        // Brigadier re-sorts the built suggestion list alphabetically, so this
        // ordering only decides which shops survive the cap.
        candidates.sort(Comparator
                .<Candidate>comparingDouble(c -> c.distance() == null ? Double.MAX_VALUE : c.distance())
                .thenComparing(c -> c.shop().name(), Comparator.nullsLast(String::compareTo)));
        return candidates.subList(0, Math.min(limit, candidates.size()));
    }

    /** Distance in blocks, or null when the shop is in another world or too far. */
    private static Double distanceTo(Location origin, Shop shop) {
        Location loc = shop.location() == null ? null : shop.location().toBukkit();
        if (loc == null || !origin.getWorld().equals(loc.getWorld())) return null;
        double squared = loc.distanceSquared(origin);
        if (squared > NEARBY_RADIUS * NEARBY_RADIUS) return null;
        return Math.sqrt(squared);
    }

    private Message tooltip(Candidate candidate) {
        MessageManager messages = support.messages();
        Shop shop = candidate.shop();
        Component owner = shop.ownerUuid() == null
                ? messages.get("command.shop-suggest.owner-none")
                : Component.text(ownerName(shop.ownerUuid()));
        Component distance = candidate.distance() == null
                ? messages.get("command.shop-suggest.distance-unknown")
                : messages.get("command.shop-suggest.distance",
                        Placeholder.parsed("blocks", String.valueOf(Math.round(candidate.distance()))));

        // Shop names are player-supplied: inserted as a component so a name
        // containing MiniMessage syntax cannot inject formatting.
        Component text = messages.get("command.shop-suggest.tooltip",
                Placeholder.component("shop_name",
                        Component.text(Displays.truncate(shop.name(), 32))),
                Placeholder.component("type", support.enumLabels().label(shop.type())),
                Placeholder.component("owner", owner),
                Placeholder.component("distance", distance),
                Placeholder.component("suspended", shop.suspended()
                        ? messages.get("command.shop-suggest.suspended-mark")
                        : Component.empty()));
        return MessageComponentSerializer.message().serialize(text);
    }

    /**
     * Suggestions are recomputed on every keystroke, so this deliberately skips
     * the DB-backed player cache: online player, then the server's local profile
     * cache, then the shortened id.
     */
    private static String ownerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String cached = Bukkit.getOfflinePlayer(uuid).getName();
        return cached != null ? cached : uuid.toString().substring(0, 8);
    }

    /** {@code distance} is null only for senders without a location (console). */
    record Candidate(String id, Shop shop, Double distance) {}
}

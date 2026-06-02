package me.f0reach.vshop.api.price;

import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the ordered list of registered providers. Mutations are infrequent
 * (plugin enable/disable boundaries) so we use a copy-on-write list for safe
 * concurrent reads from the trade/UI paths.
 */
public final class PriceRegistry {

    private final List<Entry> providers = new CopyOnWriteArrayList<>();

    public synchronized void register(Plugin plugin, PriceProvider provider) {
        providers.removeIf(e -> e.provider().id().equals(provider.id()));
        providers.add(new Entry(plugin, provider));
        // Maintain a stable order on every mutation rather than during resolve().
        List<Entry> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator.comparingInt(e -> e.provider().order()));
        providers.clear();
        providers.addAll(sorted);
    }

    public synchronized void unregisterAll(Plugin plugin) {
        providers.removeIf(e -> e.plugin().equals(plugin));
    }

    public PriceResult resolve(PriceContext ctx) {
        PriceResult result = new PriceResult(ctx.basePrice(), null, java.time.Duration.ZERO);
        if (providers.isEmpty() || !ctx.shop().isAdminShop()) return result;
        StringBuilder resolvedBy = new StringBuilder();
        for (Entry e : providers) {
            try {
                PriceResult next = e.provider().apply(ctx, result);
                if (next != null) {
                    result = mergeResult(result, next);
                    if (resolvedBy.length() > 0) resolvedBy.append(',');
                    resolvedBy.append(e.provider().id());
                }
            } catch (RuntimeException ex) {
                // Per spec §12.3.3, a faulting provider is skipped — the pipeline keeps the prior result.
                java.util.logging.Logger.getLogger(PriceRegistry.class.getName())
                        .warning("PriceProvider " + e.provider().id() + " failed: " + ex.getMessage());
            }
        }
        ctx.attrs().put("resolvedBy", resolvedBy.toString());
        return result;
    }

    private static PriceResult mergeResult(PriceResult prev, PriceResult next) {
        BigDecimal price = next.price() != null ? next.price() : prev.price();
        var reason = next.reason() != null ? next.reason() : prev.reason();
        var ttl = next.ttl() != null && !next.ttl().isZero() ? next.ttl() : prev.ttl();
        return new PriceResult(price, reason, ttl);
    }

    public List<PriceProvider> snapshot() {
        List<PriceProvider> out = new ArrayList<>(providers.size());
        for (Entry e : providers) out.add(e.provider());
        return out;
    }

    private record Entry(Plugin plugin, PriceProvider provider) {}
}

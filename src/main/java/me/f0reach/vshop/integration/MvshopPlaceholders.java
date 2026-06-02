package me.f0reach.vshop.integration;

import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.storage.StorageManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Lazily-registered PlaceholderAPI expansion. Mirrors spec §12.1's prefixes
 * {@code %mvshop_*%}. Resolution is best-effort — placeholder failure should
 * not break unrelated chat formatting, so missing data is returned as "0" / "".
 */
public final class MvshopPlaceholders extends PlaceholderExpansion {

    private final ModernVillagerShopPlugin plugin;
    private final ShopRegistry registry;
    private final StorageManager storage;
    private final EconomyService economy;

    public MvshopPlaceholders(ModernVillagerShopPlugin plugin) {
        this.plugin = plugin;
        this.registry = plugin.registry();
        this.storage = plugin.storage();
        this.economy = plugin.economyService();
    }

    @Override public String getIdentifier() { return "mvshop"; }
    @Override public String getAuthor() { return "f0reachARR"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) return "";
        // shop_count_<player>
        if (params.startsWith("shop_count_")) {
            String name = params.substring("shop_count_".length());
            OfflinePlayer p = Bukkit.getOfflinePlayer(name);
            return Integer.toString(registry.countByOwner(p.getUniqueId()));
        }
        if (params.startsWith("shop_name_")) {
            Shop s = resolveShop(params.substring("shop_name_".length()));
            return s == null ? "" : s.name();
        }
        if (params.startsWith("shop_owner_")) {
            Shop s = resolveShop(params.substring("shop_owner_".length()));
            if (s == null || s.ownerUuid() == null) return "";
            OfflinePlayer op = Bukkit.getOfflinePlayer(s.ownerUuid());
            return op.getName() == null ? "" : op.getName();
        }
        if (params.startsWith("total_sales_")) {
            return economy.format(sumByPlayer(params.substring("total_sales_".length()), TradeSide.SELL));
        }
        if (params.startsWith("total_purchases_")) {
            return economy.format(sumByPlayer(params.substring("total_purchases_".length()), TradeSide.BUY));
        }
        return null;
    }

    private Shop resolveShop(String idOrPrefix) {
        try {
            UUID id = UUID.fromString(idOrPrefix);
            return registry.byId(id).orElse(null);
        } catch (IllegalArgumentException ex) {
            for (Shop s : registry.all()) {
                if (s.id().toString().startsWith(idOrPrefix)) return s;
            }
            return null;
        }
    }

    private BigDecimal sumByPlayer(String name, TradeSide side) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        BigDecimal total = BigDecimal.ZERO;
        try {
            int offset = 0;
            while (true) {
                var batch = storage.transactions().findByPlayer(p.getUniqueId(), 200, offset);
                if (batch.isEmpty()) break;
                for (var rec : batch) {
                    if (rec.side() != side) continue;
                    // Sales = seller side, purchases = buyer side.
                    boolean credit = (side == TradeSide.SELL && p.getUniqueId().equals(rec.sellerUuid()))
                            || (side == TradeSide.BUY && p.getUniqueId().equals(rec.buyerUuid()));
                    if (!credit) continue;
                    total = total.add(rec.unitPrice().multiply(BigDecimal.valueOf(rec.amount())));
                }
                offset += batch.size();
                if (batch.size() < 200) break;
            }
        } catch (SQLException ignored) {}
        return total;
    }
}

package me.f0reach.vshop.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Mutable holder for plugin configuration. The reference is captured by
 * services at construction time; {@link #reload(FileConfiguration)} refreshes
 * the fields in place so {@code /vshop reload} reaches every holder without
 * rewiring the composition root.
 */
public final class PluginConfig {

    public enum StorageType { SQLITE, MYSQL }
    public enum LimitScope { PER_PLAYER, GLOBAL }
    public enum PlayerCacheSort { LAST_SEEN_DESC, NAME_ASC }
    public enum CloseWithInventoryMode { DISCARD, DROP, REFUSE }

    private volatile String locale;
    private volatile String fallbackLocale;
    private volatile StorageType storageType;
    private volatile String sqliteFile;
    private volatile MySqlConfig mysql;
    private volatile EconomyConfig economy;
    private volatile ShopConfig shop;
    private volatile PlayerCacheConfig playerCache;
    private volatile Set<Material> blacklist;
    private volatile boolean placeholderApiEnabled;
    private volatile ConfigurationSection uiSection;

    public PluginConfig(FileConfiguration cfg) {
        reload(cfg);
    }

    /**
     * Re-parses the given FileConfiguration into this holder in place. Callers
     * that already reference this instance see the updated values on their
     * next access — no reconstruction required.
     */
    public void reload(FileConfiguration cfg) {
        this.locale = cfg.getString("locale", "ja_JP");
        this.fallbackLocale = cfg.getString("fallbackLocale", "en_US");

        String type = cfg.getString("storage.type", "sqlite");
        this.storageType = StorageType.valueOf(type.toUpperCase(Locale.ROOT));
        this.sqliteFile = cfg.getString("storage.sqlite.file", "shops.db");
        this.mysql = new MySqlConfig(
                cfg.getString("storage.mysql.host", "localhost"),
                cfg.getInt("storage.mysql.port", 3306),
                cfg.getString("storage.mysql.database", "vshop"),
                cfg.getString("storage.mysql.username", "root"),
                cfg.getString("storage.mysql.password", ""),
                cfg.getString("storage.mysql.properties", "useUnicode=true&characterEncoding=utf8&useSSL=false"),
                cfg.getInt("storage.mysql.poolSize", 8)
        );

        double feeRate = cfg.getDouble("economy.feeRate", 0.05);
        this.economy = new EconomyConfig(
                BigDecimal.valueOf(feeRate),
                BigDecimal.valueOf(cfg.getDouble("economy.feeRateAdmin", feeRate)),
                BigDecimal.valueOf(cfg.getDouble("economy.priceMin", 1)),
                BigDecimal.valueOf(cfg.getDouble("economy.priceMax", 1_000_000)),
                cfg.getInt("economy.amountMax", 2304),
                cfg.getInt("economy.fractionDigits", 2),
                RoundingMode.valueOf(cfg.getString("economy.roundingMode", "HALF_UP").toUpperCase(Locale.ROOT)),
                BigDecimal.valueOf(cfg.getDouble("economy.priceDriftTolerance", 0.01)),
                cfg.getString("economy.currencyFormat", "<amount> <currency>"),
                cfg.getBoolean("economy.priceProvider.enabled", true)
        );

        this.shop = new ShopConfig(
                cfg.getInt("shop.maxShopsPerPlayer", -1),
                cfg.getDouble("shop.openDistance", 6.0),
                cfg.getDouble("shop.minDistance", 0.5),
                LimitScope.valueOf(cfg.getString("shop.defaultLimitScope", "PER_PLAYER").toUpperCase(Locale.ROOT)),
                cfg.getString("shop.villagerNameFormat", "<shop_name> <gray>[<primary>]</gray>"),
                cfg.getString("shop.villagerNameFormatAdmin", "<shop_name>"),
                CloseWithInventoryMode.valueOf(
                        cfg.getString("shop.closeWithInventory", "REFUSE").toUpperCase(Locale.ROOT))
        );

        this.playerCache = new PlayerCacheConfig(
                cfg.getInt("playerCache.maxEntries", 5000),
                PlayerCacheSort.valueOf(cfg.getString("playerCache.defaultSort", "LAST_SEEN_DESC").toUpperCase(Locale.ROOT)),
                parseDuration(cfg.getString("playerCache.textureTtl", "7d"))
        );

        List<String> bl = cfg.getStringList("items.blacklist");
        EnumSet<Material> mats = EnumSet.noneOf(Material.class);
        for (String name : bl) {
            Material m = Material.matchMaterial(name);
            if (m != null) mats.add(m);
        }
        this.blacklist = mats;

        this.placeholderApiEnabled = cfg.getBoolean("placeholderapi.enabled", true);
        this.uiSection = cfg.getConfigurationSection("ui");
    }

    private static Duration parseDuration(String input) {
        if (input == null || input.isBlank()) return Duration.ofDays(7);
        String s = input.trim().toLowerCase(Locale.ROOT);
        try {
            long value;
            ChronoUnit unit;
            char last = s.charAt(s.length() - 1);
            if (Character.isDigit(last)) {
                value = Long.parseLong(s);
                unit = ChronoUnit.SECONDS;
            } else {
                value = Long.parseLong(s.substring(0, s.length() - 1));
                unit = switch (last) {
                    case 's' -> ChronoUnit.SECONDS;
                    case 'm' -> ChronoUnit.MINUTES;
                    case 'h' -> ChronoUnit.HOURS;
                    case 'd' -> ChronoUnit.DAYS;
                    default -> ChronoUnit.SECONDS;
                };
            }
            return Duration.of(value, unit);
        } catch (NumberFormatException ex) {
            return Duration.ofDays(7);
        }
    }

    public String locale() { return locale; }
    public String fallbackLocale() { return fallbackLocale; }
    public StorageType storageType() { return storageType; }
    public String sqliteFile() { return sqliteFile; }
    public MySqlConfig mysql() { return mysql; }
    public EconomyConfig economy() { return economy; }
    public ShopConfig shop() { return shop; }
    public PlayerCacheConfig playerCache() { return playerCache; }
    public Set<Material> blacklist() { return blacklist; }
    public boolean placeholderApiEnabled() { return placeholderApiEnabled; }
    public ConfigurationSection uiSection() { return uiSection; }

    public boolean isBlacklisted(Material material) {
        return blacklist.contains(material);
    }

    public record MySqlConfig(String host, int port, String database, String username, String password,
                              String properties, int poolSize) {}

    public record EconomyConfig(
            BigDecimal feeRate,
            BigDecimal feeRateAdmin,
            BigDecimal priceMin,
            BigDecimal priceMax,
            int amountMax,
            int fractionDigits,
            RoundingMode roundingMode,
            BigDecimal priceDriftTolerance,
            String currencyFormat,
            boolean priceProviderEnabled
    ) {}

    public record ShopConfig(
            int maxShopsPerPlayer,
            double openDistance,
            double minDistance,
            LimitScope defaultLimitScope,
            String villagerNameFormat,
            String villagerNameFormatAdmin,
            CloseWithInventoryMode closeWithInventory
    ) {}

    public record PlayerCacheConfig(int maxEntries, PlayerCacheSort defaultSort, Duration textureTtl) {}
}

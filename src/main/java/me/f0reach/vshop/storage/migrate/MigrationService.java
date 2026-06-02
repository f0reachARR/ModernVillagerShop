package me.f0reach.vshop.storage.migrate;

import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.ModernVillagerShopPlugin;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.storage.StorageManager;

import java.sql.SQLException;
import java.util.Locale;
import java.util.logging.Logger;

/**
 * Implements {@code /vshop migrate <from> <to>} by reading the entire dataset
 * from the source repositories and replaying it into the destination. Trades
 * are NOT paused at the DB level — operators are expected to schedule this
 * during a maintenance window (spec §8.2).
 */
public final class MigrationService {

    private static final Logger LOG = Logger.getLogger(MigrationService.class.getName());

    private MigrationService() {}

    /**
     * Returns the number of shops copied. Throws if either side fails to open
     * or the running plugin is already on {@code <to>} (which would corrupt the
     * live dataset).
     */
    public static int run(ModernVillagerShopPlugin plugin, String fromType, String toType) throws Exception {
        PluginConfig.StorageType from = PluginConfig.StorageType.valueOf(fromType.toUpperCase(Locale.ROOT));
        PluginConfig.StorageType to = PluginConfig.StorageType.valueOf(toType.toUpperCase(Locale.ROOT));
        if (from == to) throw new IllegalArgumentException("from and to are the same");

        PluginConfig liveConfig = plugin.pluginConfig();
        if (liveConfig.storageType() == to) {
            throw new IllegalStateException("destination matches the currently active storage; "
                    + "switch storage.type in config first if you really want to migrate INTO live");
        }

        // The "source" half is the running plugin's repos (already wired).
        StorageManager source = plugin.storage();
        StorageManager dest = openAuxiliary(plugin, to, liveConfig);
        try {
            dest.initSchema();

            int shopsCopied = 0;
            for (var shop : source.shops().findAll()) {
                dest.shops().insert(shop);
                for (var co : source.coOwners().findByShop(shop.id())) {
                    dest.coOwners().upsert(co);
                }
                for (var slot : source.slots().findByShop(shop.id())) {
                    dest.slots().upsert(slot);
                }
                for (var inv : source.inventory().findByShop(shop.id())) {
                    dest.inventory().upsert(inv);
                }
                shopsCopied++;
            }
            LOG.info("Migration copied " + shopsCopied + " shops from " + from + " to " + to);
            return shopsCopied;
        } finally {
            dest.close();
        }
    }

    /**
     * Builds a temporary StorageManager pointing at the destination backend.
     * Reads its config from the running plugin's {@link PluginConfig} (so MySQL
     * credentials etc. come from {@code config.yml}).
     */
    private static StorageManager openAuxiliary(ModernVillagerShopPlugin plugin,
                                                PluginConfig.StorageType to,
                                                PluginConfig live) throws SQLException {
        // We build a synthetic config snapshot whose storageType is the destination.
        org.bukkit.configuration.file.YamlConfiguration shadow = new org.bukkit.configuration.file.YamlConfiguration();
        // Copy a minimal slice — only what PluginConfig and DataSourceProvider read.
        shadow.set("locale", live.locale());
        shadow.set("fallbackLocale", live.fallbackLocale());
        shadow.set("storage.type", to.name().toLowerCase(Locale.ROOT));
        shadow.set("storage.sqlite.file", "migrate-" + to.name().toLowerCase(Locale.ROOT) + ".db");
        // Reuse MySQL config from live.
        var m = live.mysql();
        shadow.set("storage.mysql.host", m.host());
        shadow.set("storage.mysql.port", m.port());
        shadow.set("storage.mysql.database", m.database());
        shadow.set("storage.mysql.username", m.username());
        shadow.set("storage.mysql.password", m.password());
        shadow.set("storage.mysql.properties", m.properties());
        shadow.set("storage.mysql.poolSize", m.poolSize());
        // Required-but-unused-for-migration sections; fall back to defaults.
        shadow.set("economy.feeRate", live.economy().feeRate().doubleValue());
        shadow.set("shop.maxShopsPerPlayer", live.shop().maxShopsPerPlayer());
        shadow.set("shop.openDistance", live.shop().openDistance());
        shadow.set("shop.minDistance", live.shop().minDistance());
        shadow.set("shop.defaultLimitScope", live.shop().defaultLimitScope().name());

        PluginConfig dst = new PluginConfig(shadow);
        StorageManager mgr = new StorageManager(plugin, dst);
        // sanity probe — open a connection to fail fast if credentials are wrong
        try (var ignored = ((HikariDataSource) mgr.dataSource()).getConnection()) {
            // ok
        }
        return mgr;
    }
}

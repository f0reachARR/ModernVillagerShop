package me.f0reach.vshop.storage;

import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.storage.mysql.MysqlCoOwnerRepository;
import me.f0reach.vshop.storage.mysql.MysqlPlayerCacheRepository;
import me.f0reach.vshop.storage.mysql.MysqlPlayerPreferenceRepository;
import me.f0reach.vshop.storage.mysql.MysqlSchemaInitializer;
import me.f0reach.vshop.storage.mysql.MysqlShopInventoryRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopLimitRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopNotificationRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopSlotRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopTransactionRepository;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import me.f0reach.vshop.storage.repo.PlayerCacheRepository;
import me.f0reach.vshop.storage.repo.PlayerPreferenceRepository;
import me.f0reach.vshop.storage.repo.SchemaInitializer;
import me.f0reach.vshop.storage.repo.ShopInventoryRepository;
import me.f0reach.vshop.storage.repo.ShopLimitRepository;
import me.f0reach.vshop.storage.repo.ShopNotificationRepository;
import me.f0reach.vshop.storage.repo.ShopRepository;
import me.f0reach.vshop.storage.repo.ShopSlotRepository;
import me.f0reach.vshop.storage.repo.ShopTransactionRepository;
import me.f0reach.vshop.storage.sqlite.SqliteCoOwnerRepository;
import me.f0reach.vshop.storage.sqlite.SqlitePlayerCacheRepository;
import me.f0reach.vshop.storage.sqlite.SqlitePlayerPreferenceRepository;
import me.f0reach.vshop.storage.sqlite.SqliteSchemaInitializer;
import me.f0reach.vshop.storage.sqlite.SqliteShopInventoryRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopLimitRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopNotificationRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopSlotRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopTransactionRepository;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Wires together a backend-specific set of repositories. The plugin should hold
 * a single {@code StorageManager} and dispose it on disable.
 */
public final class StorageManager implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final SchemaInitializer schema;
    private final ShopRepository shops;
    private final ShopSlotRepository slots;
    private final CoOwnerRepository coOwners;
    private final ShopInventoryRepository inventory;
    private final ShopLimitRepository limits;
    private final ShopTransactionRepository transactions;
    private final ShopNotificationRepository notifications;
    private final PlayerCacheRepository playerCache;
    private final PlayerPreferenceRepository playerPreferences;
    private final PluginConfig.StorageType type;

    public StorageManager(Plugin plugin, PluginConfig config) {
        this.type = config.storageType();
        this.dataSource = DataSourceProvider.create(plugin, config);
        switch (type) {
            case SQLITE -> {
                this.schema = new SqliteSchemaInitializer(dataSource);
                this.shops = new SqliteShopRepository(dataSource);
                this.slots = new SqliteShopSlotRepository(dataSource);
                this.coOwners = new SqliteCoOwnerRepository(dataSource);
                this.inventory = new SqliteShopInventoryRepository(dataSource);
                this.limits = new SqliteShopLimitRepository(dataSource);
                this.transactions = new SqliteShopTransactionRepository(dataSource);
                this.notifications = new SqliteShopNotificationRepository(dataSource);
                this.playerCache = new SqlitePlayerCacheRepository(dataSource);
                this.playerPreferences = new SqlitePlayerPreferenceRepository(dataSource);
            }
            case MYSQL -> {
                this.schema = new MysqlSchemaInitializer(dataSource);
                this.shops = new MysqlShopRepository(dataSource);
                this.slots = new MysqlShopSlotRepository(dataSource);
                this.coOwners = new MysqlCoOwnerRepository(dataSource);
                this.inventory = new MysqlShopInventoryRepository(dataSource);
                this.limits = new MysqlShopLimitRepository(dataSource);
                this.transactions = new MysqlShopTransactionRepository(dataSource);
                this.notifications = new MysqlShopNotificationRepository(dataSource);
                this.playerCache = new MysqlPlayerCacheRepository(dataSource);
                this.playerPreferences = new MysqlPlayerPreferenceRepository(dataSource);
            }
            default -> throw new IllegalStateException("unsupported storage type: " + type);
        }
    }

    public void initSchema() throws SQLException {
        schema.init();
    }

    public DataSource dataSource() { return dataSource; }
    public ShopRepository shops() { return shops; }
    public ShopSlotRepository slots() { return slots; }
    public CoOwnerRepository coOwners() { return coOwners; }
    public ShopInventoryRepository inventory() { return inventory; }
    public ShopLimitRepository limits() { return limits; }
    public ShopTransactionRepository transactions() { return transactions; }
    public ShopNotificationRepository notifications() { return notifications; }
    public PlayerCacheRepository playerCache() { return playerCache; }
    public PlayerPreferenceRepository playerPreferences() { return playerPreferences; }
    public PluginConfig.StorageType type() { return type; }

    @Override
    public void close() {
        if (!dataSource.isClosed()) dataSource.close();
    }
}

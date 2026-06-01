package me.f0reach.vshop.storage;

import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.storage.mysql.MysqlCoOwnerRepository;
import me.f0reach.vshop.storage.mysql.MysqlSchemaInitializer;
import me.f0reach.vshop.storage.mysql.MysqlShopRepository;
import me.f0reach.vshop.storage.mysql.MysqlShopSlotRepository;
import me.f0reach.vshop.storage.repo.CoOwnerRepository;
import me.f0reach.vshop.storage.repo.SchemaInitializer;
import me.f0reach.vshop.storage.repo.ShopRepository;
import me.f0reach.vshop.storage.repo.ShopSlotRepository;
import me.f0reach.vshop.storage.sqlite.SqliteCoOwnerRepository;
import me.f0reach.vshop.storage.sqlite.SqliteSchemaInitializer;
import me.f0reach.vshop.storage.sqlite.SqliteShopRepository;
import me.f0reach.vshop.storage.sqlite.SqliteShopSlotRepository;
import org.bukkit.plugin.Plugin;

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
            }
            case MYSQL -> {
                this.schema = new MysqlSchemaInitializer(dataSource);
                this.shops = new MysqlShopRepository(dataSource);
                this.slots = new MysqlShopSlotRepository(dataSource);
                this.coOwners = new MysqlCoOwnerRepository(dataSource);
            }
            default -> throw new IllegalStateException("unsupported storage type: " + type);
        }
    }

    public void initSchema() throws SQLException {
        schema.init();
    }

    public ShopRepository shops() { return shops; }
    public ShopSlotRepository slots() { return slots; }
    public CoOwnerRepository coOwners() { return coOwners; }
    public PluginConfig.StorageType type() { return type; }

    @Override
    public void close() {
        if (!dataSource.isClosed()) dataSource.close();
    }
}

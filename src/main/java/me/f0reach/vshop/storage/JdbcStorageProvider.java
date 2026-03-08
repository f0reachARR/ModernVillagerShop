package me.f0reach.vshop.storage;

import me.f0reach.vshop.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.logging.Level;

public final class JdbcStorageProvider implements StorageProvider {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final RepositoryFactory repositoryFactory;
    private HikariConnectionProvider connectionProvider;
    private ShopRepository shopRepository;
    private ListingRepository listingRepository;
    private ShopInventoryRepository shopInventoryRepository;
    private TransactionRepository transactionRepository;

    public JdbcStorageProvider(JavaPlugin plugin, PluginConfig config) {
        this(plugin, config, new RepositoryFactory());
    }

    JdbcStorageProvider(JavaPlugin plugin, PluginConfig config, RepositoryFactory repositoryFactory) {
        this.plugin = plugin;
        this.config = config;
        this.repositoryFactory = repositoryFactory;
    }

    @Override
    public void init() {
        StorageType storageType = StorageType.fromConfig(config.getStorageType());
        connectionProvider = new HikariConnectionProvider(plugin, config, storageType);

        try {
            repositoryFactory.createSchemaInitializer(storageType).initialize(connectionProvider);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database schema", e);
        }

        shopRepository = repositoryFactory.createShopRepository(storageType, connectionProvider);
        listingRepository = repositoryFactory.createListingRepository(storageType, connectionProvider);
        shopInventoryRepository = repositoryFactory.createShopInventoryRepository(storageType, connectionProvider);
        transactionRepository = repositoryFactory.createTransactionRepository(storageType, connectionProvider);
    }

    @Override
    public ShopRepository shopRepository() {
        return shopRepository;
    }

    @Override
    public ListingRepository listingRepository() {
        return listingRepository;
    }

    @Override
    public ShopInventoryRepository shopInventoryRepository() {
        return shopInventoryRepository;
    }

    @Override
    public TransactionRepository transactionRepository() {
        return transactionRepository;
    }

    @Override
    public void shutdown() {
        if (connectionProvider != null) {
            try {
                connectionProvider.shutdown();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to shutdown storage provider cleanly", e);
            }
        }
    }
}

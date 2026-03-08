package me.f0reach.vshop.storage;

public final class RepositoryFactory {
    public ShopRepository createShopRepository(StorageType storageType, ConnectionProvider connectionProvider) {
        return switch (storageType) {
            case MYSQL -> new MysqlShopRepository(connectionProvider);
            case SQLITE -> new SqliteShopRepository(connectionProvider);
        };
    }

    public ListingRepository createListingRepository(StorageType storageType, ConnectionProvider connectionProvider) {
        return switch (storageType) {
            case MYSQL -> new MysqlListingRepository(connectionProvider);
            case SQLITE -> new SqliteListingRepository(connectionProvider);
        };
    }

    public ShopInventoryRepository createShopInventoryRepository(StorageType storageType,
                                                                 ConnectionProvider connectionProvider) {
        return switch (storageType) {
            case MYSQL -> new MysqlShopInventoryRepository(connectionProvider);
            case SQLITE -> new SqliteShopInventoryRepository(connectionProvider);
        };
    }

    public TransactionRepository createTransactionRepository(StorageType storageType, ConnectionProvider connectionProvider) {
        return switch (storageType) {
            case MYSQL -> new MysqlTransactionRepository(connectionProvider);
            case SQLITE -> new SqliteTransactionRepository(connectionProvider);
        };
    }

    public SchemaInitializer createSchemaInitializer(StorageType storageType) {
        return switch (storageType) {
            case MYSQL -> new MysqlSchemaInitializer();
            case SQLITE -> new SqliteSchemaInitializer();
        };
    }
}

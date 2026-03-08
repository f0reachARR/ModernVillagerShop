package me.f0reach.vshop.storage;

import me.f0reach.vshop.storage.mysql.*;
import me.f0reach.vshop.storage.sqlite.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class RepositoryFactoryTest {
    private static final ConnectionProvider NOOP_CONNECTION_PROVIDER = () -> {
        throw new UnsupportedOperationException("Not used in this test");
    };

    private final RepositoryFactory repositoryFactory = new RepositoryFactory();

    @Test
    void createsSqliteRepositoriesAndSchemaInitializer() {
        assertInstanceOf(SqliteShopRepository.class,
                repositoryFactory.createShopRepository(StorageType.SQLITE, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(SqliteListingRepository.class,
                repositoryFactory.createListingRepository(StorageType.SQLITE, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(SqliteShopInventoryRepository.class,
                repositoryFactory.createShopInventoryRepository(StorageType.SQLITE, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(SqliteTransactionRepository.class,
                repositoryFactory.createTransactionRepository(StorageType.SQLITE, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(SqliteSchemaInitializer.class, repositoryFactory.createSchemaInitializer(StorageType.SQLITE));
    }

    @Test
    void createsMysqlRepositoriesAndSchemaInitializer() {
        assertInstanceOf(MysqlShopRepository.class,
                repositoryFactory.createShopRepository(StorageType.MYSQL, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(MysqlListingRepository.class,
                repositoryFactory.createListingRepository(StorageType.MYSQL, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(MysqlShopInventoryRepository.class,
                repositoryFactory.createShopInventoryRepository(StorageType.MYSQL, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(MysqlTransactionRepository.class,
                repositoryFactory.createTransactionRepository(StorageType.MYSQL, NOOP_CONNECTION_PROVIDER));
        assertInstanceOf(MysqlSchemaInitializer.class, repositoryFactory.createSchemaInitializer(StorageType.MYSQL));
    }
}

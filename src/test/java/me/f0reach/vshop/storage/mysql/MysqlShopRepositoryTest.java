package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.storage.ShopRepositoryContract;
import me.f0reach.vshop.testsupport.TestDatabases;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = TestDatabases.MYSQL_URL_ENV, matches = ".+")
class MysqlShopRepositoryTest extends ShopRepositoryContract {
    @Override protected Backend backend() { return Backend.MYSQL; }
}

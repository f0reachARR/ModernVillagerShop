package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.storage.ShopAppearanceRepositoryContract;
import me.f0reach.vshop.testsupport.TestDatabases;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = TestDatabases.MYSQL_URL_ENV, matches = ".+")
class MysqlShopAppearanceRepositoryTest extends ShopAppearanceRepositoryContract {
    @Override protected Backend backend() { return Backend.MYSQL; }
}

package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.storage.TimezoneSafetyContract;
import me.f0reach.vshop.testsupport.TestDatabases;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = TestDatabases.MYSQL_URL_ENV, matches = ".+")
class MysqlTimezoneSafetyTest extends TimezoneSafetyContract {
    @Override protected Backend backend() { return Backend.MYSQL; }
}

package me.f0reach.vshop.testsupport;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base for "same tests, two backends" repository suites. Subclasses pick the
 * {@link Backend} via {@link #backend()} and the shared {@code @BeforeEach}
 * builds an isolated, schema-initialised data source for each test method.
 *
 * <p>MySQL subclasses additionally annotate themselves with
 * {@code @EnabledIfEnvironmentVariable(named="VSHOP_TEST_MYSQL_URL", matches=".+")}
 * so the suite skips cleanly on machines without a MySQL daemon.
 */
public abstract class AbstractRepositoryContract {

    public enum Backend { SQLITE, MYSQL }

    protected TestDatabases.Handle handle;

    protected abstract Backend backend();

    protected HikariDataSource dataSource() {
        return handle.dataSource();
    }

    @BeforeEach
    void setUpDatabase() throws Exception {
        handle = backend() == Backend.SQLITE ? TestDatabases.sqlite() : TestDatabases.mysql();
    }

    @AfterEach
    void tearDownDatabase() throws Exception {
        if (handle != null) handle.close();
    }
}

package me.f0reach.vshop.testsupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.storage.mysql.MysqlSchemaInitializer;
import me.f0reach.vshop.storage.repo.SchemaInitializer;
import me.f0reach.vshop.storage.sqlite.SqliteSchemaInitializer;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Builds isolated, schema-initialised data sources for repository contract
 * tests. Each call returns a fresh handle owning its own HikariCP pool plus a
 * teardown hook that drops temporary state (deletes the file for SQLite,
 * drops the schema for MySQL).
 *
 * <p>MySQL is opt-in: it is only enabled when {@code VSHOP_TEST_MYSQL_URL}
 * (and optionally {@code VSHOP_TEST_MYSQL_USER}/{@code VSHOP_TEST_MYSQL_PASSWORD})
 * are present in the environment, so local runs without a MySQL daemon stay
 * green while CI exercises both backends.
 */
public final class TestDatabases {

    public static final String MYSQL_URL_ENV = "VSHOP_TEST_MYSQL_URL";
    public static final String MYSQL_USER_ENV = "VSHOP_TEST_MYSQL_USER";
    public static final String MYSQL_PASSWORD_ENV = "VSHOP_TEST_MYSQL_PASSWORD";

    private TestDatabases() {}

    public static Handle sqlite() throws Exception {
        File file = Files.createTempFile("vshop-test", ".db").toFile();
        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
        cfg.setMaximumPoolSize(1);
        HikariDataSource ds = new HikariDataSource(cfg);
        SchemaInitializer init = new SqliteSchemaInitializer(ds);
        init.init();
        return new Handle(ds, init, () -> {
            ds.close();
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        });
    }

    public static boolean isMysqlAvailable() {
        return System.getenv(MYSQL_URL_ENV) != null && !System.getenv(MYSQL_URL_ENV).isBlank();
    }

    /**
     * Opens a connection to MySQL and provisions a one-off database
     * ({@code vshop_test_<uuid>}) so concurrent test JVMs don't trample
     * each other's tables. The schema is dropped during teardown.
     */
    public static Handle mysql() throws Exception {
        String baseUrl = require(MYSQL_URL_ENV);
        String user = System.getenv(MYSQL_USER_ENV);
        String pass = System.getenv(MYSQL_PASSWORD_ENV);

        String schema = "vshop_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        createSchema(baseUrl, user, pass, schema);

        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
        cfg.setJdbcUrl(baseUrl + (baseUrl.endsWith("/") ? "" : "/") + schema
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(2);
        HikariDataSource ds = new HikariDataSource(cfg);
        SchemaInitializer init = new MysqlSchemaInitializer(ds);
        init.init();
        return new Handle(ds, init, () -> {
            try {
                ds.close();
            } finally {
                dropSchema(baseUrl, user, pass, schema);
            }
        });
    }

    private static void createSchema(String baseUrl, String user, String pass, String schema) throws SQLException {
        try (Connection c = java.sql.DriverManager.getConnection(toRootUrl(baseUrl), user, pass);
             Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void dropSchema(String baseUrl, String user, String pass, String schema) {
        try (Connection c = java.sql.DriverManager.getConnection(toRootUrl(baseUrl), user, pass);
             Statement st = c.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS " + schema);
        } catch (SQLException ignore) {
            // best-effort cleanup; CI runs schedule throwaway servers anyway.
        }
    }

    private static String toRootUrl(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String require(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("env var " + envVar + " is required for MySQL tests");
        }
        return value;
    }

    public static final class Handle implements AutoCloseable {
        private final HikariDataSource dataSource;
        private final SchemaInitializer initializer;
        private final AutoCloseable teardown;

        private Handle(HikariDataSource dataSource, SchemaInitializer initializer, AutoCloseable teardown) {
            this.dataSource = dataSource;
            this.initializer = initializer;
            this.teardown = teardown;
        }

        public HikariDataSource dataSource() { return dataSource; }
        public SchemaInitializer initializer() { return initializer; }

        @Override public void close() throws Exception {
            teardown.close();
        }
    }
}

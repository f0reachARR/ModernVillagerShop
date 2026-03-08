package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ConnectionProvider;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class SqliteTestDatabase {
    private final ConnectionProvider connectionProvider;

    private SqliteTestDatabase(ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    static SqliteTestDatabase create(Path tempDir, String fileName) throws SQLException {
        String jdbcUrl = "jdbc:sqlite:" + tempDir.resolve(fileName);
        ConnectionProvider provider = () -> DriverManager.getConnection(jdbcUrl);
        new SqliteSchemaInitializer().initialize(provider);
        return new SqliteTestDatabase(provider);
    }

    ConnectionProvider connectionProvider() {
        return connectionProvider;
    }

    Connection openConnection() throws SQLException {
        return connectionProvider.getConnection();
    }
}

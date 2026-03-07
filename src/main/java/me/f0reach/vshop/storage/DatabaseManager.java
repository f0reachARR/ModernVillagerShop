package me.f0reach.vshop.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private HikariDataSource dataSource;
    private boolean mysql;

    public DatabaseManager(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void init() {
        String type = config.getStorageType();
        mysql = "mysql".equalsIgnoreCase(type);

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ModernVillagerShop-Pool");

        if (mysql) {
            hikari.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort()
                    + "/" + config.getMysqlDatabase() + "?useSSL=false&allowPublicKeyRetrieval=true");
            hikari.setUsername(config.getMysqlUser());
            hikari.setPassword(config.getMysqlPassword());
            hikari.setMaximumPoolSize(10);
        } else {
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
        }

        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "25");

        dataSource = new HikariDataSource(hikari);
        createTables();
    }

    private void createTables() {
        String autoIncrement = mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String intType = mysql ? "INT" : "INTEGER";
        String blobType = mysql ? "MEDIUMBLOB" : "BLOB";
        String timestampDefault = mysql ? "CURRENT_TIMESTAMP" : "CURRENT_TIMESTAMP";

        String shopsTable = "CREATE TABLE IF NOT EXISTS shops ("
                + "shop_id " + intType + " PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", "
                + "type VARCHAR(16) NOT NULL, "
                + "villager_uuid VARCHAR(36) NOT NULL UNIQUE, "
                + "owner_uuid VARCHAR(36), "
                + "world VARCHAR(64) NOT NULL, "
                + "x DOUBLE NOT NULL, "
                + "y DOUBLE NOT NULL, "
                + "z DOUBLE NOT NULL, "
                + "active BOOLEAN NOT NULL DEFAULT 1, "
                + "created_at TIMESTAMP NOT NULL DEFAULT " + timestampDefault + ", "
                + "updated_at TIMESTAMP NOT NULL DEFAULT " + timestampDefault
                + ")";

        String listingsTable = "CREATE TABLE IF NOT EXISTS listings ("
                + "listing_id " + intType + " PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", "
                + "shop_id " + intType + " NOT NULL, "
                + "mode VARCHAR(8) NOT NULL, "
                + "item_serialized " + blobType + " NOT NULL, "
                + "unit_price DOUBLE NOT NULL, "
                + "stock " + intType + " NOT NULL DEFAULT 0, "
                + "target_stock " + intType + " NOT NULL DEFAULT 0, "
                + "enabled BOOLEAN NOT NULL DEFAULT 1, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT " + timestampDefault + ", "
                + "FOREIGN KEY (shop_id) REFERENCES shops(shop_id) ON DELETE CASCADE"
                + ")";

        String transactionsTable = "CREATE TABLE IF NOT EXISTS transactions ("
                + "tx_id " + intType + " PRIMARY KEY " + (mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT") + ", "
                + "shop_id " + intType + " NOT NULL, "
                + "listing_id " + intType + " NOT NULL, "
                + "direction VARCHAR(16) NOT NULL, "
                + "buyer_uuid VARCHAR(36) NOT NULL, "
                + "seller_uuid VARCHAR(36), "
                + "qty " + intType + " NOT NULL, "
                + "gross DOUBLE NOT NULL, "
                + "fee DOUBLE NOT NULL, "
                + "net DOUBLE NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT " + timestampDefault + ", "
                + "FOREIGN KEY (shop_id) REFERENCES shops(shop_id), "
                + "FOREIGN KEY (listing_id) REFERENCES listings(listing_id)"
                + ")";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            if (!mysql) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            stmt.execute(shopsTable);
            stmt.execute(listingsTable);
            stmt.execute(transactionsTable);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create database tables", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public boolean isMysql() {
        return mysql;
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

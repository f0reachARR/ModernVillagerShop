package me.f0reach.vshop.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.config.PluginConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public final class HikariConnectionProvider implements ConnectionProvider {
    private final HikariDataSource dataSource;

    public HikariConnectionProvider(JavaPlugin plugin, PluginConfig config, StorageType storageType) {
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("ModernVillagerShop-Pool");

        if (storageType == StorageType.MYSQL) {
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
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }
}

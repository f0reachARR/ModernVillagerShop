package me.f0reach.vshop.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.f0reach.vshop.config.PluginConfig;
import org.bukkit.plugin.Plugin;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Builds a HikariCP DataSource for SQLite or MySQL based on plugin config.
 */
public final class DataSourceProvider {

    private DataSourceProvider() {}

    public static HikariDataSource create(Plugin plugin, PluginConfig config) {
        HikariConfig hk = new HikariConfig();
        hk.setPoolName("vshop-pool");
        if (config.storageType() == PluginConfig.StorageType.SQLITE) {
            File db = new File(plugin.getDataFolder(), config.sqliteFile());
            try {
                if (db.getParentFile() != null && !db.getParentFile().exists()) {
                    Files.createDirectories(db.getParentFile().toPath());
                }
            } catch (IOException ex) {
                throw new RuntimeException("Failed to ensure DB dir", ex);
            }
            hk.setDriverClassName("org.sqlite.JDBC");
            hk.setJdbcUrl("jdbc:sqlite:" + db.getAbsolutePath());
            // SQLite has very limited concurrent writers. Single connection ensures
            // writer serialization without distributed locking complexity.
            hk.setMaximumPoolSize(1);
            hk.addDataSourceProperty("foreign_keys", "true");
        } else {
            PluginConfig.MySqlConfig my = config.mysql();
            hk.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hk.setJdbcUrl("jdbc:mysql://" + my.host() + ":" + my.port() + "/" + my.database() + "?" + my.properties());
            hk.setUsername(my.username());
            hk.setPassword(my.password());
            hk.setMaximumPoolSize(Math.max(2, my.poolSize()));
        }
        return new HikariDataSource(hk);
    }

    public static DataSource create(Plugin plugin) {
        throw new UnsupportedOperationException();
    }
}

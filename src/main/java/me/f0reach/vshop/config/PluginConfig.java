package me.f0reach.vshop.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfig {
    private final JavaPlugin plugin;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }

    public String getLocale() { return config().getString("locale", "ja_JP"); }
    public String getFallbackLocale() { return config().getString("fallbackLocale", "en_US"); }
    public String getStorageType() { return config().getString("storage.type", "sqlite"); }
    public String getMysqlHost() { return config().getString("storage.mysql.host", "127.0.0.1"); }
    public int getMysqlPort() { return config().getInt("storage.mysql.port", 3306); }
    public String getMysqlDatabase() { return config().getString("storage.mysql.database", "modernvillagershop"); }
    public String getMysqlUser() { return config().getString("storage.mysql.user", "root"); }
    public String getMysqlPassword() { return config().getString("storage.mysql.password", ""); }
    public double getFeeRate() { return config().getDouble("trade.feeRate", 0.03); }
    public int getMaxTradeItemTypes() { return config().getInt("shop.maxTradeItemTypesPerShop", 16); }
}

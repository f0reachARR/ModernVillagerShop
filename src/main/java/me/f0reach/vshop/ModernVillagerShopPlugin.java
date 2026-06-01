package me.f0reach.vshop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

@SuppressWarnings("UnstableApiUsage")
public final class ModernVillagerShopPlugin extends JavaPlugin {

    private PluginConfig config;
    private MessageManager messages;
    private StorageManager storage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfigInternal();

        this.storage = new StorageManager(this, config);
        try {
            storage.initSchema();
        } catch (SQLException ex) {
            getLogger().severe("Failed to initialize storage: " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("ModernVillagerShop enabled (locale=" + messages.primaryLocale()
                + ", storage=" + config.storageType() + ")");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
            storage = null;
        }
        getLogger().info("ModernVillagerShop disabled");
    }

    public void reloadConfigInternal() {
        reloadConfig();
        this.config = new PluginConfig(getConfig());
        if (this.messages == null) {
            this.messages = new MessageManager(this);
        }
        this.messages.load(config.locale(), config.fallbackLocale());
    }

    public PluginConfig pluginConfig() {
        return config;
    }

    public MessageManager messages() {
        return messages;
    }

    public StorageManager storage() {
        return storage;
    }
}

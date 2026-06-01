package me.f0reach.vshop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("UnstableApiUsage")
public final class ModernVillagerShopPlugin extends JavaPlugin {

    private PluginConfig config;
    private MessageManager messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfigInternal();
        getLogger().info("ModernVillagerShop enabled (locale=" + messages.primaryLocale()
                + ", storage=" + config.storageType() + ")");
    }

    @Override
    public void onDisable() {
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
}

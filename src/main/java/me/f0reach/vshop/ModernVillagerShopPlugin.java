package me.f0reach.vshop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.ShopVillagerManager;
import me.f0reach.vshop.shop.egg.SpawnEggFactory;
import me.f0reach.vshop.shop.listener.ShopEggListener;
import me.f0reach.vshop.shop.listener.ShopVillagerListener;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

@SuppressWarnings("UnstableApiUsage")
public final class ModernVillagerShopPlugin extends JavaPlugin {

    private PluginConfig config;
    private MessageManager messages;
    private StorageManager storage;
    private ShopRegistry registry;
    private ShopService shopService;
    private SpawnEggFactory eggFactory;
    private ShopVillagerManager villagerManager;

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

        this.registry = new ShopRegistry();
        this.villagerManager = new ShopVillagerManager(this, messages, storage.coOwners());
        this.shopService = new ShopService(storage, registry, villagerManager, config);
        this.eggFactory = new SpawnEggFactory(this, messages);

        try {
            shopService.loadAll();
        } catch (SQLException ex) {
            getLogger().severe("Failed to load existing shops: " + ex.getMessage());
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ShopEggListener(this, eggFactory, shopService, messages), this);
        pm.registerEvents(new ShopVillagerListener(registry, shopService, villagerManager, config), this);

        getLogger().info("ModernVillagerShop enabled (locale=" + messages.primaryLocale()
                + ", storage=" + config.storageType() + ", shops=" + registry.all().size() + ")");
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

    public PluginConfig pluginConfig() { return config; }
    public MessageManager messages() { return messages; }
    public StorageManager storage() { return storage; }
    public ShopRegistry registry() { return registry; }
    public ShopService shopService() { return shopService; }
    public SpawnEggFactory eggFactory() { return eggFactory; }
    public ShopVillagerManager villagerManager() { return villagerManager; }
}

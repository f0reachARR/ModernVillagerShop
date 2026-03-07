package me.f0reach.vshop;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.f0reach.vshop.command.VShopCommands;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.VaultEconomyAdapter;
import me.f0reach.vshop.listener.ShopListener;
import me.f0reach.vshop.listener.VillagerProtectListener;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.SpawnEggManager;
import me.f0reach.vshop.storage.DatabaseManager;
import me.f0reach.vshop.storage.ListingRepository;
import me.f0reach.vshop.storage.ShopInventoryRepository;
import me.f0reach.vshop.storage.ShopRepository;
import me.f0reach.vshop.storage.TransactionRepository;
import me.f0reach.vshop.ui.UIManager;
import me.f0reach.vshop.ui.listener.UIEventListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

@SuppressWarnings("UnstableApiUsage")
public final class Plugin extends JavaPlugin {
    private DatabaseManager databaseManager;
    private PluginConfig pluginConfig;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        try {
            // Phase 1: Config
            pluginConfig = new PluginConfig(this);

            // Phase 2: Locale
            messageManager = new MessageManager(this, pluginConfig);

            // Phase 3: Database
            databaseManager = new DatabaseManager(this, pluginConfig);
            databaseManager.init();

            // Phase 4: Economy
            VaultEconomyAdapter economy = new VaultEconomyAdapter();
            economy.init();

            // Phase 5: Repositories
            ShopRepository shopRepo = new ShopRepository(databaseManager);
            ListingRepository listingRepo = new ListingRepository(databaseManager);
            ShopInventoryRepository shopInventoryRepo = new ShopInventoryRepository(databaseManager);
            TransactionRepository txRepo = new TransactionRepository(databaseManager);

            // Phase 6: Services
            SpawnEggManager eggManager = new SpawnEggManager(this, messageManager);
            ShopService shopService = new ShopService(this, pluginConfig, messageManager,
                    shopRepo, listingRepo, shopInventoryRepo, txRepo, economy);

            // Phase 7: UI
            UIManager uiManager = new UIManager(this, messageManager, shopService);

            // Phase 8: Listeners
            VillagerProtectListener protectListener = new VillagerProtectListener(this, shopRepo);
            protectListener.initOnStartup();

            getServer().getPluginManager().registerEvents(
                    new ShopListener(this, shopService, eggManager, uiManager, messageManager), this);
            getServer().getPluginManager().registerEvents(protectListener, this);
            getServer().getPluginManager().registerEvents(new UIEventListener(), this);

            // Phase 9: Commands
            Runnable reloadAction = () -> {
                pluginConfig.reload();
                messageManager.reload();
            };
            VShopCommands commands = new VShopCommands(pluginConfig, messageManager, shopService,
                    eggManager, uiManager, reloadAction);

            this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
                event.registrar().register(commands.buildCommandTree(),
                        "VillagerShop management commands",
                        java.util.List.of("vs"));
            });

            getLogger().info("ModernVillagerShop enabled successfully!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable ModernVillagerShop", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        getLogger().info("ModernVillagerShop disabled.");
    }
}

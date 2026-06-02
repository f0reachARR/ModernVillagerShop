package me.f0reach.vshop;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.f0reach.vshop.command.VShopCommand;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.shop.ShopOpenService;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.ShopVillagerManager;
import me.f0reach.vshop.shop.coowner.CoOwnerFlow;
import me.f0reach.vshop.shop.edit.ShopEditListener;
import me.f0reach.vshop.shop.edit.ShopEditService;
import me.f0reach.vshop.shop.edit.SlotEditFlow;
import me.f0reach.vshop.shop.egg.SpawnEggFactory;
import me.f0reach.vshop.shop.listener.NotificationFlushListener;
import me.f0reach.vshop.shop.listener.ShopEggListener;
import me.f0reach.vshop.shop.listener.ShopVillagerListener;
import me.f0reach.vshop.shop.trade.TradeFlow;
import me.f0reach.vshop.shop.trade.TradeNotifier;
import me.f0reach.vshop.shop.trade.TradeService;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.ui.chest.IconConfig;
import me.f0reach.vshop.ui.chest.ShopBrowseListener;
import me.f0reach.vshop.ui.chest.ShopBrowseUi;
import me.f0reach.vshop.ui.chest.ShopEditUi;
import me.f0reach.vshop.ui.dialog.DialogService;
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
    private DialogService dialogService;
    private IconConfig iconConfig;
    private ShopBrowseUi browseUi;
    private ShopOpenService openService;
    private EconomyService economyService;
    private TradeService tradeService;
    private TradeNotifier tradeNotifier;
    private TradeFlow tradeFlow;
    private ShopEditService editService;
    private ShopEditUi editUi;
    private SlotEditFlow slotEditFlow;
    private CoOwnerFlow coOwnerFlow;

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

        this.economyService = new EconomyService(this, config);
        if (!economyService.setup()) {
            getLogger().severe("Vault Economy is required but unavailable. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.registry = new ShopRegistry();
        this.villagerManager = new ShopVillagerManager(this, messages, storage.coOwners());
        this.shopService = new ShopService(storage, registry, villagerManager, config);
        this.eggFactory = new SpawnEggFactory(this, messages);
        this.dialogService = new DialogService(this);
        this.iconConfig = new IconConfig(messages, config.uiSection());
        this.browseUi = new ShopBrowseUi(storage, iconConfig, messages);
        this.openService = new ShopOpenService(browseUi, messages, config);
        this.tradeNotifier = new TradeNotifier(this, messages, storage, economyService);
        this.editService = new ShopEditService(storage);
        this.tradeService = new TradeService(storage, economyService, config, tradeNotifier, editService);
        this.tradeFlow = new TradeFlow(dialogService, tradeService, messages, economyService, config);
        this.editUi = new ShopEditUi(storage, iconConfig, messages);
        this.slotEditFlow = new SlotEditFlow(dialogService, messages, economyService, editService, config);
        this.coOwnerFlow = new CoOwnerFlow(dialogService, messages, storage, shopService, villagerManager);

        try {
            shopService.loadAll();
        } catch (SQLException ex) {
            getLogger().severe("Failed to load existing shops: " + ex.getMessage());
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ShopEggListener(this, eggFactory, shopService, messages), this);
        pm.registerEvents(new ShopVillagerListener(registry, shopService, villagerManager, openService, config), this);
        pm.registerEvents(new ShopBrowseListener(registry, browseUi, storage, tradeFlow, messages), this);
        pm.registerEvents(new NotificationFlushListener(this, tradeNotifier), this);
        pm.registerEvents(new ShopEditListener(registry, editUi, editService, slotEditFlow, storage, messages), this);

        VShopCommand cmd = new VShopCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(cmd.build(),
                        "ModernVillagerShop main command", java.util.List.of("vs")));

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
    public DialogService dialogService() { return dialogService; }
    public ShopBrowseUi browseUi() { return browseUi; }
    public ShopOpenService openService() { return openService; }
    public EconomyService economyService() { return economyService; }
    public TradeService tradeService() { return tradeService; }
    public TradeFlow tradeFlow() { return tradeFlow; }
    public TradeNotifier tradeNotifier() { return tradeNotifier; }
    public ShopEditService editService() { return editService; }
    public ShopEditUi editUi() { return editUi; }
    public SlotEditFlow slotEditFlow() { return slotEditFlow; }
    public CoOwnerFlow coOwnerFlow() { return coOwnerFlow; }
}

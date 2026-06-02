package me.f0reach.vshop;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.f0reach.vshop.api.ModernVillagerShopAPI;
import me.f0reach.vshop.api.price.PriceRegistry;
import me.f0reach.vshop.command.VShopCommand;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.economy.EconomyService;
import me.f0reach.vshop.integration.MvshopPlaceholders;
import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.shop.ShopOpenService;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.shop.ShopVillagerManager;
import me.f0reach.vshop.shop.cache.PlayerCacheService;
import me.f0reach.vshop.shop.coowner.CoOwnerFlow;
import me.f0reach.vshop.shop.edit.ShopActionMenu;
import me.f0reach.vshop.shop.edit.ShopEditListener;
import me.f0reach.vshop.shop.edit.ShopEditService;
import me.f0reach.vshop.shop.edit.SlotEditFlow;
import me.f0reach.vshop.shop.egg.SpawnEggFactory;
import me.f0reach.vshop.shop.listener.NotificationFlushListener;
import me.f0reach.vshop.shop.listener.PlayerCacheListener;
import me.f0reach.vshop.shop.listener.ShopEggListener;
import me.f0reach.vshop.shop.listener.ShopVillagerListener;
import me.f0reach.vshop.shop.trade.TradeFlow;
import me.f0reach.vshop.shop.trade.TradeNotifier;
import me.f0reach.vshop.shop.trade.TradeService;
import me.f0reach.vshop.storage.StorageManager;
import me.f0reach.vshop.ui.chest.IconConfig;
import me.f0reach.vshop.ui.chest.PlayerPickerListener;
import me.f0reach.vshop.ui.chest.PlayerPickerUi;
import me.f0reach.vshop.ui.chest.ShopBrowseListener;
import me.f0reach.vshop.ui.chest.ShopBrowseUi;
import me.f0reach.vshop.ui.chest.ShopEditUi;
import me.f0reach.vshop.ui.chest.ShopRestockListener;
import me.f0reach.vshop.ui.chest.ShopRestockUi;
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
    private PriceRegistry priceRegistry;
    private ModernVillagerShopAPI api;
    private MvshopPlaceholders papiExpansion;
    private PlayerCacheService playerCacheService;
    private PlayerPickerUi playerPickerUi;
    private ShopRestockUi restockUi;
    private ShopActionMenu actionMenu;

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
        this.editService = new ShopEditService(storage, registry);
        this.tradeService = new TradeService(storage, economyService, config, tradeNotifier, editService);
        this.tradeFlow = new TradeFlow(dialogService, tradeService, messages, economyService, config);
        this.editUi = new ShopEditUi(storage, iconConfig, messages);
        this.slotEditFlow = new SlotEditFlow(dialogService, messages, economyService, editService, config);
        this.playerCacheService = new PlayerCacheService(this);
        this.playerPickerUi = new PlayerPickerUi(playerCacheService, messages, dialogService);
        this.coOwnerFlow = new CoOwnerFlow(dialogService, messages, storage, shopService, villagerManager,
                playerPickerUi, playerCacheService);
        this.restockUi = new ShopRestockUi(storage, messages, editService);
        this.actionMenu = new ShopActionMenu(this, dialogService, messages, editService, restockUi, coOwnerFlow);
        this.priceRegistry = new PriceRegistry();
        this.api = new ModernVillagerShopAPI(registry, storage, priceRegistry);
        getServer().getServicesManager().register(ModernVillagerShopAPI.class, api, this,
                org.bukkit.plugin.ServicePriority.Normal);

        try {
            shopService.loadAll();
        } catch (SQLException ex) {
            getLogger().severe("Failed to load existing shops: " + ex.getMessage());
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ShopEggListener(this, eggFactory, shopService, messages), this);
        pm.registerEvents(new ShopVillagerListener(registry, shopService, villagerManager, openService,
                actionMenu, config), this);
        pm.registerEvents(new ShopBrowseListener(registry, browseUi, storage, tradeFlow, messages), this);
        pm.registerEvents(new NotificationFlushListener(this, tradeNotifier), this);
        pm.registerEvents(new ShopEditListener(registry, editUi, editService, slotEditFlow,
                storage, messages), this);
        pm.registerEvents(new ShopRestockListener(this, storage, messages, editService, config, restockUi), this);
        pm.registerEvents(new PlayerPickerListener(this, playerPickerUi), this);
        pm.registerEvents(new PlayerCacheListener(playerCacheService), this);

        VShopCommand cmd = new VShopCommand(this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(cmd.build(),
                        "ModernVillagerShop main command", java.util.List.of("vs")));

        // Hook up PlaceholderAPI when present + enabled in config.
        if (config.placeholderApiEnabled()
                && getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            this.papiExpansion = new MvshopPlaceholders(this);
            if (papiExpansion.register()) {
                getLogger().info("Registered PlaceholderAPI expansion.");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion.");
            }
        }

        getLogger().info("ModernVillagerShop enabled (locale=" + messages.primaryLocale()
                + ", storage=" + config.storageType() + ", shops=" + registry.all().size() + ")");
    }

    @Override
    public void onDisable() {
        if (papiExpansion != null) {
            try { papiExpansion.unregister(); } catch (Throwable ignored) {}
            papiExpansion = null;
        }
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
    public PriceRegistry priceRegistry() { return priceRegistry; }
    public ModernVillagerShopAPI api() { return api; }
    public PlayerCacheService playerCacheService() { return playerCacheService; }
    public PlayerPickerUi playerPickerUi() { return playerPickerUi; }
    public ShopRestockUi restockUi() { return restockUi; }
    public ShopActionMenu actionMenu() { return actionMenu; }
}

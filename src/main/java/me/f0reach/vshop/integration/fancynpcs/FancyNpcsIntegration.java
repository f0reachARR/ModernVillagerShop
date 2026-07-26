package me.f0reach.vshop.integration.fancynpcs;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.events.NpcsLoadedEvent;
import de.oliver.fancynpcs.api.skins.SkinData;
import de.oliver.fancynpcs.api.skins.SkinLoadException;
import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.model.ShopEntityKind;
import me.f0reach.vshop.shop.ShopInteractionRouter;
import me.f0reach.vshop.shop.ShopRegistry;
import me.f0reach.vshop.shop.entity.ShopAppearanceRegistry;
import me.f0reach.vshop.shop.entity.ShopDisplayName;
import me.f0reach.vshop.shop.entity.ShopEntityBackend;
import me.f0reach.vshop.shop.entity.ShopEntityIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Owns the FancyNpcs side of the plugin: the backend, the interaction listener,
 * and the boot/shutdown of every NPC-backed shop.
 *
 * <p>NPCs cannot be created during our {@code onEnable} — FancyNpcs has not
 * finished loading its own at that point ({@code npcManager.isLoaded()} is
 * false, and it fires {@link NpcsLoadedEvent} a few seconds later). So
 * {@code start} either spawns immediately or waits for that event.
 */
public final class FancyNpcsIntegration implements ShopEntityIntegration, Listener {

    private final Plugin plugin;
    private final ShopRegistry shops;
    private final ShopAppearanceRegistry appearances;
    private final FancyNpcBackend backend;

    private boolean spawned;

    public FancyNpcsIntegration(Plugin plugin, ShopRegistry shops, ShopAppearanceRegistry appearances,
                                ShopDisplayName displayName, PluginConfig config) {
        this.plugin = plugin;
        this.shops = shops;
        this.appearances = appearances;
        this.backend = new FancyNpcBackend(plugin, displayName, appearances, config);
    }

    @Override
    public ShopEntityBackend backend() {
        return backend;
    }

    @Override
    public void start(ShopInteractionRouter router) {
        Bukkit.getPluginManager().registerEvents(new FancyNpcListener(shops, router), plugin);
        if (FancyNpcsPlugin.get().getNpcManager().isLoaded()) {
            // Happens when we are (re)loaded by a plugin manager after boot.
            spawnAll();
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onNpcsLoaded(NpcsLoadedEvent event) {
        spawnAll();
    }

    @Override
    public void shutdown() {
        for (Shop shop : npcBackedShops()) {
            try {
                backend.remove(shop);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to remove NPC for shop " + shop.id() + ": " + ex);
            }
        }
        spawned = false;
    }

    private synchronized void spawnAll() {
        if (spawned) return; // isLoaded() raced with the event
        spawned = true;

        List<Shop> targets = npcBackedShops();
        if (targets.isEmpty()) return;

        // Resolving a skin blocks the calling thread on a cache miss (~0.7s per
        // unseen name against Mojang), so warm the cache off-thread first and
        // only then build the NPCs, where the same lookups are a cache hit.
        Set<SkinRequest> skins = new LinkedHashSet<>();
        for (Shop shop : targets) {
            ShopAppearance a = appearances.getOrDefault(shop.id());
            EntityType type = a.entityType() == null ? EntityType.PLAYER : a.entityType();
            if (a.skin() != null && type == EntityType.PLAYER) {
                skins.add(new SkinRequest(a.skin(),
                        a.skinVariant() == me.f0reach.vshop.model.SkinVariant.SLIM
                                ? SkinData.SkinVariant.SLIM : SkinData.SkinVariant.AUTO));
            }
        }

        if (skins.isEmpty()) {
            spawnOnMain(targets);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            warmSkinCache(skins);
            Bukkit.getScheduler().runTask(plugin, () -> spawnOnMain(targets));
        });
    }

    private void warmSkinCache(Set<SkinRequest> skins) {
        var manager = FancyNpcsPlugin.get().getSkinManager();
        for (SkinRequest request : skins) {
            try {
                manager.getByIdentifier(request.identifier(), request.variant());
            } catch (SkinLoadException ex) {
                // Reported again per shop by the backend, with the shop id attached.
                plugin.getLogger().warning("Could not pre-load skin '" + request.identifier()
                        + "': " + ex.getReason());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skin pre-load failed for '" + request.identifier() + "': " + ex);
            }
        }
    }

    private void spawnOnMain(List<Shop> targets) {
        int created = 0;
        for (Shop shop : targets) {
            Location at = shop.location().toBukkit();
            if (at == null) {
                plugin.getLogger().warning("Shop " + shop.id() + " wants an NPC but its world is not loaded");
                continue;
            }
            try {
                backend.spawn(shop, at);
                created++;
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Failed to create NPC for shop " + shop.id() + ": " + ex);
            }
        }
        plugin.getLogger().info("FancyNpcs: created " + created + "/" + targets.size() + " shop NPCs");
    }

    private List<Shop> npcBackedShops() {
        List<Shop> out = new ArrayList<>();
        for (Shop shop : shops.all()) {
            if (appearances.backendOf(shop.id()) == ShopEntityKind.FANCY_NPC) out.add(shop);
        }
        return out;
    }

    private record SkinRequest(String identifier, SkinData.SkinVariant variant) {}
}

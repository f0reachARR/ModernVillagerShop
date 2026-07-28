package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
import me.f0reach.vshop.model.ShopEntityKind;
import me.f0reach.vshop.shop.ShopService;
import me.f0reach.vshop.storage.repo.ShopAppearanceRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Write path for a shop's appearance: mutate, persist, re-render.
 *
 * <p>Callers describe the change and this keeps the three copies in step — the
 * {@code shop_appearance} row, the in-memory {@link ShopAppearanceRegistry}, and
 * whatever is standing in the world.
 */
public final class ShopAppearanceService {

    private final Plugin plugin;
    private final ShopAppearanceRepository repository;
    private final ShopAppearanceRegistry registry;
    private final ShopEntityService entities;
    private final ShopService shops;

    public ShopAppearanceService(Plugin plugin, ShopAppearanceRepository repository,
                                 ShopAppearanceRegistry registry, ShopEntityService entities,
                                 ShopService shops) {
        this.plugin = plugin;
        this.repository = repository;
        this.registry = registry;
        this.entities = entities;
        this.shops = shops;
    }

    /** The shop's stored appearance, or the implicit Villager default. */
    public ShopAppearance current(Shop shop) {
        return registry.getOrDefault(shop.id());
    }

    /**
     * Applies {@code mutation}, writes it through, and rebuilds the shop's
     * representation. {@code onRendered} runs on the main thread once the change
     * is actually visible — later than this call returns, because the render may
     * need an off-thread skin lookup first.
     *
     * <p>An appearance mutated back to all-defaults deletes its row instead of
     * storing a no-op, keeping "no row means plain Villager" true.
     */
    public void apply(Shop shop, Consumer<ShopAppearance> mutation, Runnable onRendered) throws SQLException {
        ShopAppearance appearance = registry.find(shop.id())
                .orElseGet(() -> ShopAppearance.defaultFor(shop.id()));
        ShopEntityKind before = appearance.backend();

        mutation.accept(appearance);
        appearance.setUpdatedAt(Instant.now());

        if (appearance.isDefault()) {
            repository.delete(shop.id());
            registry.remove(shop.id());
        } else {
            repository.upsert(appearance);
            registry.put(appearance);
        }

        boolean backendChanged = before != registry.backendOf(shop.id());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            entities.prepare(shop);
            Bukkit.getScheduler().runTask(plugin, () -> {
                render(shop, backendChanged);
                if (onRendered != null) onRendered.run();
            });
        });
    }

    /** Drops every override and returns the shop to a plain Villager. */
    public void reset(Shop shop, Runnable onRendered) throws SQLException {
        apply(shop, appearance -> {
            appearance.setBackend(ShopEntityKind.VILLAGER);
            appearance.setEntityType(null);
            appearance.setSkin(null);
            appearance.setSkinVariant(null);
            appearance.setGlowing(false);
            appearance.setGlowColor(null);
            appearance.setScale(null);
            appearance.setTurnToPlayer(null);
            appearance.attributes().clear();
            appearance.equipment().clear();
        }, onRendered);
    }

    private void render(Shop shop, boolean backendChanged) {
        if (!backendChanged) {
            entities.refresh(shop);
            return;
        }
        // The old representation belongs to the other backend, so respawn tears
        // down both. The new entity id (null for NPCs) has to be persisted.
        UUID entityId = entities.respawn(shop);
        shop.setVillagerEntityId(entityId);
        try {
            shops.update(shop);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to persist the new entity id for shop " + shop.id(), ex);
        }
    }
}

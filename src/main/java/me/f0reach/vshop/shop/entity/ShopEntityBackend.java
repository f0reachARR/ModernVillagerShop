package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopAppearance;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Strategy for the in-world representation of a shop — the thing a player walks
 * up to and clicks: a real Villager ({@link VillagerBackend}) or a packet NPC
 * ({@code integration.fancynpcs.FancyNpcBackend}). Callers depend on
 * {@link ShopEntityService} rather than on a concrete backend.
 *
 * <p>All methods run on the main thread and are best-effort: if the shop's
 * representation is not currently live (unloaded chunk, missing entity), the
 * refresh/remove calls are no-ops rather than errors.
 */
public interface ShopEntityBackend {

    /**
     * Creates the in-world representation at {@code at} and returns the Bukkit
     * entity id to persist on the shop, or {@code null} for backends that have
     * no Bukkit entity. The caller owns writing the result to the {@link Shop}.
     */
    UUID spawn(Shop shop, Location at);

    /** Re-applies every derived attribute (profession, name, flags) to a live representation. */
    void refresh(Shop shop);

    /** Cheap path for a name-only change: re-renders the custom name and nothing else. */
    void refreshDisplayName(Shop shop);

    /** Despawns the representation. Does not touch persistence. */
    void remove(Shop shop);

    /**
     * Off-main-thread warm-up for an appearance that is about to be spawned or
     * refreshed. Backends whose rendering needs a slow lookup — FancyNpcs
     * resolves a skin name against Mojang, blocking the caller for the better
     * part of a second on a cache miss — do it here so the main thread only
     * hits the cache. Must be safe to call from any thread, and safe to skip.
     */
    default void prepare(ShopAppearance appearance) {}
}

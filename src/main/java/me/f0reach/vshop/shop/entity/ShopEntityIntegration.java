package me.f0reach.vshop.shop.entity;

import me.f0reach.vshop.shop.ShopInteractionRouter;

/**
 * An optional third-party plugin that can render shops.
 *
 * <p>The interface exists so the composition root can hold a reference to the
 * integration without ever naming the concrete class outside a
 * "is that plugin installed?" guard — naming it would risk the JVM resolving
 * the third-party types on a server where they do not exist.
 */
public interface ShopEntityIntegration {

    /** The backend to register with {@link ShopEntityService}. */
    ShopEntityBackend backend();

    /**
     * Registers interaction handling and brings every shop assigned to this
     * backend into the world. Called once shops and appearances are loaded; the
     * implementation is responsible for waiting on whatever readiness signal its
     * own plugin requires.
     *
     * <p>Takes the router as a parameter rather than a constructor argument
     * because the router transitively needs the shop action menu, which needs
     * services that in turn need the backend this integration provides.
     */
    void start(ShopInteractionRouter router);

    /** Removes everything this integration created. Called from {@code onDisable}. */
    void shutdown();
}

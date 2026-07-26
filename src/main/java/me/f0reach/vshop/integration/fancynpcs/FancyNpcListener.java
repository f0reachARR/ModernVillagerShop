package me.f0reach.vshop.integration.fancynpcs;

import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.ShopInteractionRouter;
import me.f0reach.vshop.shop.ShopRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Turns a right-click on a shop NPC into the same flow a right-clicked shop
 * villager produces.
 *
 * <p>FancyNpcs fires {@code NpcInteractEvent} from its handler for Paper's
 * {@code PlayerUseUnknownEntityEvent}. Both are synchronous Bukkit events, so
 * this runs on the main thread and needs no scheduler hop.
 */
public final class FancyNpcListener implements Listener {

    private final ShopRegistry registry;
    private final ShopInteractionRouter router;

    public FancyNpcListener(ShopRegistry registry, ShopInteractionRouter router) {
        this.registry = registry;
        this.router = router;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(NpcInteractEvent event) {
        // Left-click stays inert, matching the villager: punching a shop does nothing.
        if (event.getInteractionType() != ActionTrigger.RIGHT_CLICK) return;

        UUID shopId = FancyNpcBackend.shopIdOf(event.getNpc().getData().getName());
        if (shopId == null) return; // somebody else's NPC

        Shop shop = registry.byId(shopId).orElse(null);
        if (shop == null) return;
        router.onRightClick(event.getPlayer(), shop);
    }
}

package me.f0reach.vshop.shop;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.shop.edit.ShopActionMenu;
import me.f0reach.vshop.sound.SoundEvents;
import me.f0reach.vshop.sound.SoundService;
import org.bukkit.entity.Player;

/**
 * What happens when a player right-clicks a shop, independent of what they
 * actually clicked. Villagers arrive here from {@code PlayerInteractEntityEvent}
 * and FancyNpcs NPCs from {@code NpcInteractEvent}; both must behave the same.
 */
public final class ShopInteractionRouter {

    private final ShopOpenService openService;
    private final ShopActionMenu actionMenu;
    private final SoundService sounds;

    public ShopInteractionRouter(ShopOpenService openService, ShopActionMenu actionMenu, SoundService sounds) {
        this.openService = openService;
        this.actionMenu = actionMenu;
        this.sounds = sounds;
    }

    public void onRightClick(Player viewer, Shop shop) {
        // Owners / privileged co-owners get the action menu directly; others see the customer view.
        sounds.play(viewer, SoundEvents.UI_OPEN);
        if (actionMenu.canShow(viewer, shop)) {
            actionMenu.open(viewer, shop);
            return;
        }
        openService.open(viewer, shop);
    }
}

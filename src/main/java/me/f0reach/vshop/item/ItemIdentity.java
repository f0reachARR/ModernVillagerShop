package me.f0reach.vshop.item;

import me.f0reach.vshop.config.PluginConfig;
import org.bukkit.inventory.ItemStack;

/**
 * Item-equality / blacklist policy. Identity is based on {@link ItemStack#isSimilar},
 * which compares material + metadata + PDC but ignores stack size.
 */
public final class ItemIdentity {

    private ItemIdentity() {}

    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        return a.isSimilar(b);
    }

    public static boolean isBlacklisted(PluginConfig config, ItemStack item) {
        return item == null || config.isBlacklisted(item.getType());
    }

    public static ItemStack copyTemplate(ItemStack item) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        copy.setAmount(1);
        return copy;
    }
}

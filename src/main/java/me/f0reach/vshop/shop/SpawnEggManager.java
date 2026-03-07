package me.f0reach.vshop.shop;

import me.f0reach.vshop.locale.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class SpawnEggManager {
    private final NamespacedKey shopEggKey;
    private final MessageManager messages;

    public SpawnEggManager(JavaPlugin plugin, MessageManager messages) {
        this.shopEggKey = new NamespacedKey(plugin, "shop_egg");
        this.messages = messages;
    }

    public ItemStack createShopEgg() {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        meta.displayName(messages.get("shop.egg_name"));
        meta.lore(List.of(
                messages.get("shop.egg_lore_1"),
                messages.get("shop.egg_lore_2")
        ));
        meta.getPersistentDataContainer().set(shopEggKey, PersistentDataType.BYTE, (byte) 1);
        egg.setItemMeta(meta);
        return egg;
    }

    public boolean isShopEgg(ItemStack item) {
        if (item == null || item.getType() != Material.VILLAGER_SPAWN_EGG) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(shopEggKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getShopEggKey() {
        return shopEggKey;
    }
}

package me.f0reach.vshop.shop;

import me.f0reach.vshop.locale.MessageManager;
import me.f0reach.vshop.model.ShopType;
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
    private final NamespacedKey shopEggTypeKey;
    private final MessageManager messages;

    public SpawnEggManager(JavaPlugin plugin, MessageManager messages) {
        this.shopEggKey = new NamespacedKey(plugin, "shop_egg");
        this.shopEggTypeKey = new NamespacedKey(plugin, "shop_egg_type");
        this.messages = messages;
    }

    public ItemStack createShopEgg() {
        return createShopEgg(ShopType.PLAYER);
    }

    public ItemStack createAdminShopEgg() {
        return createShopEgg(ShopType.ADMIN);
    }

    private ItemStack createShopEgg(ShopType type) {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        if (type == ShopType.ADMIN) {
            meta.displayName(messages.get("shop.admin_egg_name"));
            meta.lore(List.of(
                    messages.get("shop.admin_egg_lore_1"),
                    messages.get("shop.admin_egg_lore_2")
            ));
        } else {
            meta.displayName(messages.get("shop.egg_name"));
            meta.lore(List.of(
                    messages.get("shop.egg_lore_1"),
                    messages.get("shop.egg_lore_2")
            ));
        }
        meta.getPersistentDataContainer().set(shopEggKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(shopEggTypeKey, PersistentDataType.STRING, type.name());
        egg.setItemMeta(meta);
        return egg;
    }

    public boolean isShopEgg(ItemStack item) {
        if (item == null || item.getType() != Material.VILLAGER_SPAWN_EGG) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(shopEggKey, PersistentDataType.BYTE);
    }

    public ShopType getShopType(ItemStack item) {
        if (!isShopEgg(item)) {
            return ShopType.PLAYER;
        }
        ItemMeta meta = item.getItemMeta();
        String rawType = meta.getPersistentDataContainer().get(shopEggTypeKey, PersistentDataType.STRING);
        if (rawType == null || rawType.isBlank()) {
            return ShopType.PLAYER; // compatibility for old eggs
        }
        try {
            return ShopType.valueOf(rawType);
        } catch (IllegalArgumentException ignored) {
            return ShopType.PLAYER;
        }
    }

    public NamespacedKey getShopEggKey() {
        return shopEggKey;
    }
}

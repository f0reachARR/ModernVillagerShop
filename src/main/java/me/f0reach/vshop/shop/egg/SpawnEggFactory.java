package me.f0reach.vshop.shop.egg;

import me.f0reach.vshop.locale.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds shop spawn-egg ItemStacks. PDC fields:
 *   vshop:rows  (Integer) - row count; -1 for infinite
 *   vshop:admin (Byte 0/1) - admin variant flag
 */
public final class SpawnEggFactory {

    private final MessageManager messages;
    private final NamespacedKey keyRows;
    private final NamespacedKey keyAdmin;

    public SpawnEggFactory(Plugin plugin, MessageManager messages) {
        this.messages = messages;
        this.keyRows = new NamespacedKey(plugin, "rows");
        this.keyAdmin = new NamespacedKey(plugin, "admin");
    }

    public NamespacedKey keyRows() { return keyRows; }
    public NamespacedKey keyAdmin() { return keyAdmin; }

    public ItemStack create(SpawnEggMeta meta) {
        ItemStack stack = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta im = stack.getItemMeta();
        PersistentDataContainer pdc = im.getPersistentDataContainer();
        pdc.set(keyRows, PersistentDataType.INTEGER, meta.rowCount());
        pdc.set(keyAdmin, PersistentDataType.BYTE, (byte) (meta.admin() ? 1 : 0));

        Component name = messages.get("shop.egg.name",
                Placeholder.parsed("type", meta.displayType()));
        im.displayName(name);

        List<Component> lore = new ArrayList<>();
        if (meta.admin()) {
            lore.add(messages.get("shop.egg.lore-admin"));
        } else {
            lore.add(messages.get("shop.egg.lore-lines",
                    Placeholder.parsed("lines", meta.displayType())));
        }
        im.lore(lore);

        stack.setItemMeta(im);
        return stack;
    }

    /**
     * Reads spawn-egg metadata from a stack, or empty if the stack is not a
     * recognized shop spawn egg.
     */
    public java.util.Optional<SpawnEggMeta> read(ItemStack stack) {
        if (stack == null || stack.getType() != Material.VILLAGER_SPAWN_EGG) {
            return java.util.Optional.empty();
        }
        ItemMeta im = stack.getItemMeta();
        if (im == null) return java.util.Optional.empty();
        PersistentDataContainer pdc = im.getPersistentDataContainer();
        if (!pdc.has(keyRows, PersistentDataType.INTEGER)) return java.util.Optional.empty();
        int rows = pdc.getOrDefault(keyRows, PersistentDataType.INTEGER, 1);
        byte admin = pdc.getOrDefault(keyAdmin, PersistentDataType.BYTE, (byte) 0);
        return java.util.Optional.of(new SpawnEggMeta(rows, admin != 0));
    }
}

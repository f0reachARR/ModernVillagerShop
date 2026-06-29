package me.f0reach.vshop.ui.chest;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Shared neutral filler for chest-UI nav-row backgrounds and out-of-bounds slot blocking. */
final class ChestFiller {

    private ChestFiller() {}

    static ItemStack neutralPane() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

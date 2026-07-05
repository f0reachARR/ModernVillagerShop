package me.f0reach.vshop.ui.chest;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.locale.MessageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code ui.chest.icons.*} from config and builds template item stacks
 * for chest-UI navigation elements (next/prev page, close, sort, filter, etc.).
 */
public final class IconConfig {

    private final PluginConfig config;
    private final MiniMessage mm;

    public IconConfig(MessageManager messages, PluginConfig config) {
        this.mm = messages.miniMessage();
        this.config = config;
    }

    public ItemStack icon(String key, Material defaultMaterial, String defaultName) {
        ConfigurationSection ui = config.uiSection();
        ConfigurationSection root = ui == null ? null : ui.getConfigurationSection("chest.icons");
        ConfigurationSection sec = root == null ? null : root.getConfigurationSection(key);
        Material material = defaultMaterial;
        String name = defaultName;
        List<String> lore = new ArrayList<>();
        Integer modelData = null;
        if (sec != null) {
            String mat = sec.getString("material");
            if (mat != null) {
                Material parsed = Material.matchMaterial(mat);
                if (parsed != null) material = parsed;
            }
            String n = sec.getString("name");
            if (n != null) name = n;
            lore = sec.getStringList("lore");
            if (sec.contains("customModelData")) {
                modelData = sec.getInt("customModelData");
            }
        }
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(mm.deserialize(name));
            if (!lore.isEmpty()) {
                List<net.kyori.adventure.text.Component> components = new ArrayList<>(lore.size());
                for (String line : lore) components.add(mm.deserialize(line));
                meta.lore(components);
            }
            if (modelData != null) meta.setCustomModelData(modelData);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}

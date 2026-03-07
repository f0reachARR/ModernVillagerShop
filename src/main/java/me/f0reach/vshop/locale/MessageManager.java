package me.f0reach.vshop.locale;

import me.f0reach.vshop.config.PluginConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MessageManager {
    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration primary;
    private YamlConfiguration fallback;

    public MessageManager(JavaPlugin plugin, PluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        primary = loadLocale(config.getLocale());
        fallback = loadLocale(config.getFallbackLocale());
    }

    private YamlConfiguration loadLocale(String locale) {
        String fileName = "lang/messages_" + localeToFileKey(locale) + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        // Also load defaults from jar
        InputStream is = plugin.getResource(fileName);
        if (is != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(is, StandardCharsets.UTF_8));
            yaml.setDefaults(defaults);
        }
        return yaml;
    }

    private String localeToFileKey(String locale) {
        // ja_JP -> ja, en_US -> en
        if (locale.contains("_")) {
            return locale.split("_")[0];
        }
        return locale;
    }

    public String getRaw(String key) {
        String val = primary.getString(key);
        if (val == null) val = fallback.getString(key);
        if (val == null) val = "<red>[Missing: " + key + "]</red>";
        return val;
    }

    public Component get(String key) {
        return miniMessage.deserialize(getRaw(key));
    }

    public Component get(String key, String... placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            builder.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        return miniMessage.deserialize(getRaw(key), builder.build());
    }

    public Component get(String key, TagResolver resolver) {
        return miniMessage.deserialize(getRaw(key), resolver);
    }

    public MiniMessage miniMessage() {
        return miniMessage;
    }
}

package me.f0reach.vshop.locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads MiniMessage-formatted localization YAML files and resolves keys with the
 * fallback order: selected locale -> fallback locale -> final hard-coded fallback.
 */
public final class MessageManager {

    private final Plugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private Map<String, String> primary = new HashMap<>();
    private Map<String, String> fallback = new HashMap<>();
    private String primaryLocale;
    private String fallbackLocale;

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load(String primaryLocale, String fallbackLocale) {
        this.primaryLocale = primaryLocale;
        this.fallbackLocale = fallbackLocale;
        this.primary = loadOrCopy(primaryLocale);
        this.fallback = loadOrCopy(fallbackLocale);
    }

    private Map<String, String> loadOrCopy(String locale) {
        String name = "lang/messages_" + lang(locale) + ".yml";
        File external = new File(plugin.getDataFolder(), name);
        if (!external.exists()) {
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    external.getParentFile().mkdirs();
                    java.nio.file.Files.copy(in, external.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to extract " + name + ": " + e.getMessage());
            }
        }

        YamlConfiguration yml;
        if (external.exists()) {
            yml = YamlConfiguration.loadConfiguration(external);
        } else {
            InputStream in = plugin.getResource(name);
            if (in == null) return new HashMap<>();
            yml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }

        Map<String, String> flat = new LinkedHashMap<>();
        for (String key : yml.getKeys(true)) {
            Object val = yml.get(key);
            if (val instanceof String s) flat.put(key, s);
        }
        return flat;
    }

    private static String lang(String locale) {
        if (locale == null) return "en";
        // Accept formats like "ja_JP" / "en_US" / "ja".
        int idx = locale.indexOf('_');
        return idx > 0 ? locale.substring(0, idx) : locale;
    }

    public Component get(String key, TagResolver... resolvers) {
        String raw = primary.get(key);
        if (raw == null) raw = fallback.get(key);
        if (raw == null) raw = key; // hard fallback
        return miniMessage.deserialize(raw, resolvers);
    }

    public Component get(String key, Map<String, String> placeholders) {
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> (TagResolver) Placeholder.parsed(e.getKey(), e.getValue()))
                .toArray(TagResolver[]::new);
        return get(key, resolvers);
    }

    public String getRaw(String key) {
        String raw = primary.get(key);
        if (raw == null) raw = fallback.get(key);
        return raw != null ? raw : key;
    }

    public String primaryLocale() { return primaryLocale; }
    public String fallbackLocale() { return fallbackLocale; }
    public MiniMessage miniMessage() { return miniMessage; }
}

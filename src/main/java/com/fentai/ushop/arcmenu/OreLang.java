package com.fentai.ushop.arcmenu;

import cn.superiormc.ultimateshop.managers.LanguageManager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-player text from plugins/UltimateShop-ArcMenu/lang/&lt;locale&gt;.yml, in the language UltimateShop uses for
 * that player, so the addon's words never mix with UltimateShop's. A missing key falls back to the bundled file of
 * the same language, then to English.
 */
final class OreLang {

    private static final List<String> BUNDLED = List.of("en_US", "zh_CN");

    private final JavaPlugin plugin;

    private final Map<String, YamlConfiguration> files = new HashMap<>();

    private final Map<String, YamlConfiguration> bundled = new HashMap<>();

    OreLang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    void reload() {
        files.clear();
        bundled.clear();
        File folder = new File(plugin.getDataFolder(), "lang");
        for (String name : BUNDLED) {
            if (!new File(folder, name + ".yml").exists()) {
                plugin.saveResource("lang/" + name + ".yml", false);
            }
            YamlConfiguration resource = resource(name);
            if (resource != null) {
                bundled.put(name.toLowerCase(Locale.ROOT), resource);
            }
        }
        File[] list = folder.listFiles((dir, file) -> file.endsWith(".yml"));
        if (list != null) {
            for (File file : list) {
                files.put(file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT),
                    YamlConfiguration.loadConfiguration(file));
            }
        }
    }

    String get(Player player, String key, String fallback, String... replacements) {
        return replace(text(locale(player), key, fallback), replacements);
    }

    /** The text in every loaded language. */
    List<String> all(String key, String fallback, String... replacements) {
        Set<String> locales = new LinkedHashSet<>(bundled.keySet());
        locales.addAll(files.keySet());
        List<String> result = new ArrayList<>();
        for (String locale : locales) {
            result.add(replace(text(locale, key, fallback), replacements));
        }
        return result;
    }

    private static String locale(Player player) {
        try {
            return LanguageManager.languageManager.getPlayerLanguage(player).replace('-', '_').toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError error) {
            return player == null ? "en_us" : player.getLocale().toLowerCase(Locale.ROOT);
        }
    }

    private String text(String locale, String key, String fallback) {
        String text = lookup(files.get(locale), key);
        if (text == null) {
            text = lookup(bundled.get(locale), key);
        }
        if (text == null) {
            text = lookup(files.get("en_us"), key);
        }
        if (text == null) {
            text = lookup(bundled.get("en_us"), key);
        }
        return text == null ? fallback : text;
    }

    private static String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return text;
    }

    static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String lookup(YamlConfiguration file, String key) {
        return file == null ? null : file.getString(key);
    }

    private YamlConfiguration resource(String name) {
        try (InputStream input = plugin.getResource("lang/" + name + ".yml")) {
            if (input == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return null;
        }
    }
}

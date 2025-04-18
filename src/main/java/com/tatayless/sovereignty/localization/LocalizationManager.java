package com.tatayless.sovereignty.localization;

import com.tatayless.sovereignty.Sovereignty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class LocalizationManager {
    private final Sovereignty plugin;
    private final String language;
    private final Map<String, FileConfiguration> languageConfigs;
    private final MiniMessage miniMessage;

    public LocalizationManager(Sovereignty plugin, String language) {
        this.plugin = plugin;
        this.language = language;
        this.languageConfigs = new HashMap<>();
        this.miniMessage = MiniMessage.miniMessage();

        loadLanguages();
    }

    private void loadLanguages() {
        // Load default language (en_US)
        loadLanguage("en_US");

        // Load selected language if it's not the default
        if (!language.equals("en_US")) {
            loadLanguage(language);
        }
    }

    private void loadLanguage(String lang) {
        File langFile = new File(plugin.getDataFolder(), "languages/" + lang + ".yml");

        if (!langFile.exists()) {
            plugin.saveResource("languages/" + lang + ".yml", false);
        }

        try {
            languageConfigs.put(lang, YamlConfiguration.loadConfiguration(langFile));
            // Also load from jar as fallback
            InputStream defaultStream = plugin.getResource("languages/" + lang + ".yml");
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                languageConfigs.get(lang).setDefaults(defaultConfig);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load language file: " + lang + ".yml");
            e.printStackTrace();
        }
    }

    public String getMessage(String key, String... placeholders) {
        String message = null;

        // Try to get from selected language
        if (languageConfigs.containsKey(language)) {
            message = languageConfigs.get(language).getString(key);
        }

        // Fallback to default language
        if (message == null && languageConfigs.containsKey("en_US")) {
            message = languageConfigs.get("en_US").getString(key);
        }

        // Final fallback
        if (message == null) {
            return "Missing translation key: " + key;
        }

        // Replace placeholders
        if (placeholders != null && placeholders.length > 0) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    message = message.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
                }
            }
        }

        return message;
    }

    public Component getComponent(String key, String... placeholders) {
        String message = getMessage(key, placeholders);
        return miniMessage.deserialize(message);
    }

    public void reloadLanguages() {
        languageConfigs.clear();
        loadLanguages();
    }
}

package com.tatayless.sovereignty.localization;

import com.tatayless.sovereignty.Sovereignty;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class LocalizationManager {
    private final Sovereignty plugin;
    private final String defaultLanguage = "en_US";
    private String currentLanguage;
    private final Map<String, FileConfiguration> languageFiles = new HashMap<>();

    public LocalizationManager(Sovereignty plugin) {
        this.plugin = plugin;
        this.currentLanguage = plugin.getConfig().getString("language", defaultLanguage);
        loadLanguages();
    }

    private void loadLanguages() {
        // Always load default language
        loadLanguage(defaultLanguage);

        // Load current language if different from default
        if (!currentLanguage.equals(defaultLanguage)) {
            loadLanguage(currentLanguage);
        }
    }

    private void loadLanguage(String language) {
        File languageFile = new File(plugin.getDataFolder(), "languages/" + language + ".yml");

        if (!languageFile.exists()) {
            // Create language file from resource
            try (InputStream in = plugin.getResource("languages/" + language + ".yml")) {
                if (in != null) {
                    // Ensure directory exists
                    languageFile.getParentFile().mkdirs();

                    // Save resource to file
                    plugin.saveResource("languages/" + language + ".yml", false);

                    // Load configuration from file
                    languageFiles.put(language, YamlConfiguration.loadConfiguration(languageFile));
                    plugin.getLogger().info("Loaded language file: " + language + ".yml");
                } else {
                    plugin.getLogger().warning("Missing language file: " + language + ".yml");

                    // Create an empty language file
                    if (language.equals(defaultLanguage)) {
                        createDefaultLanguageFile(languageFile);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load language file: " + language + ".yml");
                e.printStackTrace();
            }
        } else {
            // Load existing language file
            languageFiles.put(language, YamlConfiguration.loadConfiguration(languageFile));
        }

        // If we still don't have a language file, create a default one for the default
        // language
        if (!languageFiles.containsKey(language) && language.equals(defaultLanguage)) {
            createDefaultLanguageFile(languageFile);
        }
    }

    private void createDefaultLanguageFile(File file) {
        try {
            file.getParentFile().mkdirs();

            FileConfiguration config = new YamlConfiguration();

            // Add default messages
            config.set("plugin.enabled", "Sovereignty has been enabled!");
            config.set("plugin.disabled", "Sovereignty has been disabled!");
            config.set("database.initialized", "Database connection established successfully!");
            config.set("database.error", "Failed to initialize database. Check your configuration!");
            config.set("command.no_permission", "You don't have permission to use this command!");

            // Save the configuration
            config.save(file);

            // Add to loaded languages
            languageFiles.put(defaultLanguage, config);

            plugin.getLogger().info("Created default language file: " + file.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create default language file!");
            e.printStackTrace();
        }
    }

    public String getMessage(String key) {
        return getMessage(key, true);
    }

    public String getMessage(String key, boolean colorize) {
        String message = null;

        // Try to get message from current language
        if (languageFiles.containsKey(currentLanguage)) {
            message = languageFiles.get(currentLanguage).getString(key);
        }

        // Fall back to default language if message not found
        if (message == null && languageFiles.containsKey(defaultLanguage)) {
            message = languageFiles.get(defaultLanguage).getString(key);
        }

        // Use a default message if still not found
        if (message == null) {
            message = "Missing message key: " + key;
        }

        // Apply color codes if requested
        if (colorize) {
            message = LegacyComponentSerializer.legacyAmpersand().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        }

        return message;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setCurrentLanguage(String language) {
        if (!languageFiles.containsKey(language)) {
            loadLanguage(language);
        }

        if (languageFiles.containsKey(language)) {
            currentLanguage = language;
            plugin.getConfig().set("language", language);
            plugin.saveConfig();
        }
    }
}

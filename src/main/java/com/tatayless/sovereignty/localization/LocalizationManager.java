package com.tatayless.sovereignty.localization;

import com.tatayless.sovereignty.Sovereignty;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

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
        }
    }

    /**
     * Gets a localized message with placeholders replaced.
     * 
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     * @return The raw message string with placeholders replaced
     */
    public String getMessage(String key, String... placeholders) {
        String rawMessage = null;

        // Try to get from selected language
        if (languageConfigs.containsKey(language)) {
            rawMessage = languageConfigs.get(language).getString(key);
        }

        // Fallback to default language
        if (rawMessage == null && languageConfigs.containsKey("en_US")) {
            rawMessage = languageConfigs.get("en_US").getString(key);
        }

        // Final fallback
        if (rawMessage == null) {
            return "Missing translation key: " + key;
        }

        // Replace placeholders
        return replacePlaceholders(rawMessage, placeholders);
    }

    /**
     * Gets a localized message as a Component with MiniMessage formatting.
     * 
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     * @return The formatted message as a Component
     */
    public Component getComponent(String key, String... placeholders) {
        String message = getMessage(key, placeholders);
        return miniMessage.deserialize(message);
    }

    /**
     * Gets a localized message that can be sent to players with proper formatting.
     * 
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     * @return A special wrapper that can be sent to players with proper formatting
     */
    public LocalizedMessage getLocalizedMessage(String key, String... placeholders) {
        String message = getMessage(key, placeholders);
        return new LocalizedMessage(message, miniMessage.deserialize(message));
    }

    /**
     * Gets a localized message with placeholders replaced and parsed with
     * MiniMessage.
     * 
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     * @return The formatted message string with MiniMessage tags processed
     * @deprecated Use getComponent() instead which properly preserves formatting
     */
    @Deprecated
    public String getFormattedMessage(String key, String... placeholders) {
        return getMessage(key, placeholders);
    }

    /**
     * Wrapper class for localized messages that properly handles formatting when
     * sent to players.
     */
    public class LocalizedMessage {
        private final String rawMessage;
        private final Component component;

        public LocalizedMessage(String rawMessage, Component component) {
            this.rawMessage = rawMessage;
            this.component = component;
        }

        /**
         * @return The raw message with placeholders replaced but without formatting
         */
        @Override
        public String toString() {
            return rawMessage;
        }

        /**
         * Sends the formatted message to a player or any command sender
         * 
         * @param recipient The recipient of the message
         */
        public void sendTo(CommandSender recipient) {
            recipient.sendMessage(component);
        }

        /**
         * Sends the formatted message to any Adventure audience
         * 
         * @param audience The audience to send to
         */
        public void sendTo(Audience audience) {
            audience.sendMessage(component);
        }

        /**
         * Broadcasts the message to all online players
         */
        public void broadcast() {
            plugin.getServer().sendMessage(component);
        }

        /**
         * Get the formatted component
         * 
         * @return The Component with MiniMessage formatting applied
         */
        public Component asComponent() {
            return component;
        }
    }

    /**
     * Replaces placeholders in a message.
     * 
     * @param message      The message to process
     * @param placeholders Placeholders in the format [key1, value1, key2, value2,
     *                     ...]
     * @return The message with placeholders replaced
     */
    private String replacePlaceholders(String message, String... placeholders) {
        if (message == null)
            return "";
        if (placeholders == null || placeholders.length == 0)
            return message;

        String result = message;
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }

        return result;
    }

    public void reloadLanguages() {
        languageConfigs.clear();
        loadLanguages();
    }

    /**
     * Sends a localized message to a CommandSender with proper MiniMessage
     * formatting.
     * 
     * @param sender       The recipient of the message
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     */
    public void sendMessage(CommandSender sender, String key, String... placeholders) {
        Component component = getComponent(key, placeholders);
        sender.sendMessage(component);
    }

    /**
     * Sends a localized message to a Player with proper MiniMessage formatting.
     * 
     * @param player       The player to send the message to
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     */
    public void sendMessage(Player player, String key, String... placeholders) {
        Component component = getComponent(key, placeholders);
        player.sendMessage(component);
    }

    /**
     * Sends a localized message to any Adventure Audience with proper MiniMessage
     * formatting.
     * 
     * @param audience     The audience to send the message to
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     */
    public void sendMessage(Audience audience, String key, String... placeholders) {
        Component component = getComponent(key, placeholders);
        audience.sendMessage(component);
    }

    /**
     * Broadcasts a localized message to all players on the server with proper
     * MiniMessage formatting.
     * 
     * @param key          The translation key
     * @param placeholders Placeholders in the format [key1, value1, key2,
     *                     value2,...]
     */
    public void broadcastMessage(String key, String... placeholders) {
        Component component = getComponent(key, placeholders);
        plugin.getServer().sendMessage(component);
    }
}

package com.tatayless.sovereignty;

import com.tatayless.sovereignty.config.ConfigManager;
import com.tatayless.sovereignty.database.DatabaseManager;
import com.tatayless.sovereignty.localization.LocalizationManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Sovereignty extends JavaPlugin {
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private LocalizationManager localizationManager;

    @Override
    public void onEnable() {
        // Save default configuration if it doesn't exist
        saveDefaultConfig();

        // Initialize managers
        this.configManager = new ConfigManager(this);
        this.localizationManager = new LocalizationManager(this);
        this.databaseManager = new DatabaseManager(this, configManager);

        try {
            // Initialize database
            databaseManager.initialize();
            getLogger().info(localizationManager.getMessage("database.initialized"));
        } catch (Exception e) {
            getLogger().severe(localizationManager.getMessage("database.error"));
            getLogger().severe(e.getMessage());
            // Disable the plugin if database initialization fails
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info(localizationManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        // Close database connections
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info(localizationManager.getMessage("plugin.disabled"));
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }
}

package com.tatayless.sovereignty;

import com.tatayless.sovereignty.commands.CommandManager;
import com.tatayless.sovereignty.config.ConfigManager;
import com.tatayless.sovereignty.database.DatabaseManager;
import com.tatayless.sovereignty.listeners.ListenerManager;
import com.tatayless.sovereignty.localization.LocalizationManager;
import com.tatayless.sovereignty.commands.admin.AdminCommandExecutor;
import com.tatayless.sovereignty.services.ServiceManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class Sovereignty extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private LocalizationManager localizationManager;
    private ServiceManager serviceManager;
    private CommandManager commandManager;
    private ListenerManager listenerManager;

    @Override
    public void onEnable() {
        // Initialize configuration
        configManager = new ConfigManager(this);
        configManager.loadConfig();

        // Initialize localization
        localizationManager = new LocalizationManager(this, configManager.getLanguage());

        // Initialize database
        databaseManager = new DatabaseManager(this, configManager);
        try {
            databaseManager.initialize();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize services
        serviceManager = new ServiceManager(this);
        serviceManager.initializeServices();

        // Initialize commands
        commandManager = new CommandManager(this);
        commandManager.registerCommands();

        // Register admin command
        AdminCommandExecutor adminCommandExecutor = new AdminCommandExecutor(this);
        getCommand("admin").setExecutor(adminCommandExecutor);
        getCommand("admin").setTabCompleter(adminCommandExecutor);

        // Initialize event listeners
        listenerManager = new ListenerManager(this);
        listenerManager.registerListeners();

        getLogger().info("Sovereignty plugin has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("Sovereignty plugin has been disabled!");
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

    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    /**
     * Get the command manager
     * 
     * @return The command manager
     */
    public CommandManager getCommandManager() {
        return commandManager;
    }
}

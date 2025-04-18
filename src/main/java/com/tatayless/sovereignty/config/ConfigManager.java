package com.tatayless.sovereignty.config;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    private final Sovereignty plugin;
    private FileConfiguration config;

    public ConfigManager(Sovereignty plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public void saveConfig() {
        plugin.saveConfig();
    }

    public String getDatabaseType() {
        return config.getString("database.type", "sqlite").toLowerCase();
    }

    public boolean isMySQL() {
        return getDatabaseType().equals("mysql");
    }

    public boolean isSQLite() {
        return getDatabaseType().equals("sqlite");
    }

    public String getDefaultLanguage() {
        return "en_US";
    }

    public String getLanguage() {
        return config.getString("language", getDefaultLanguage());
    }

    public String getMySQLHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySQLPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySQLDatabase() {
        return config.getString("database.mysql.database", "sovereignty");
    }

    public String getMySQLUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMySQLPassword() {
        return config.getString("database.mysql.password", "password");
    }

    public boolean getMySQLUseSSL() {
        return config.getBoolean("database.mysql.useSSL", false);
    }

    public String getSQLiteFilename() {
        return config.getString("database.sqlite.filename", "sovereignty.db");
    }

    public FileConfiguration getConfig() {
        return config;
    }
}

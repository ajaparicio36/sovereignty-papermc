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

    // Nation Settings
    public int getMaxChunksForPowerLevel(int powerLevel) {
        switch (powerLevel) {
            case 1:
                return config.getInt("power-scaling.chunks.level-1", 10);
            case 2:
                return config.getInt("power-scaling.chunks.level-2", 25);
            case 3:
                return config.getInt("power-scaling.chunks.level-3", 50);
            case 4:
                return config.getInt("power-scaling.chunks.level-4", 80);
            case 5:
                return config.getInt("power-scaling.chunks.level-5", 120);
            case 6:
                return config.getInt("power-scaling.chunks.level-6", 180);
            default:
                return 10;
        }
    }

    public int getSoldierLivesForPowerLevel(int powerLevel) {
        switch (powerLevel) {
            case 1:
                return config.getInt("power-scaling.soldier-lives.level-1", 5);
            case 2:
                return config.getInt("power-scaling.soldier-lives.level-2", 10);
            case 3:
                return config.getInt("power-scaling.soldier-lives.level-3", 20);
            case 4:
                return config.getInt("power-scaling.soldier-lives.level-4", 30);
            case 5:
                return config.getInt("power-scaling.soldier-lives.level-5", 50);
            case 6:
                return config.getInt("power-scaling.soldier-lives.level-6", 75);
            default:
                return 5;
        }
    }

    // War Settings
    public boolean isWarDestructionEnabled() {
        return config.getBoolean("war.enable-destruction", false);
    }

    public boolean isAssassinationModeEnabled() {
        return config.getBoolean("war.enable-assassination", false);
    }

    public double getAnnexationPercentage() {
        double percentage = config.getDouble("war.annexation-percentage", 0.25);
        // Ensure value is between 0 and 1
        return Math.max(0.0, Math.min(1.0, percentage));
    }

    // Vault Settings
    public int getNationVaultRows() {
        return config.getInt("vaults.nation-vault-rows", 3);
    }

    public int getTradeVaultRows() {
        return config.getInt("vaults.trade-vault-rows", 3);
    }

    public int getVaultExpiryTimeMinutes() {
        return config.getInt("vaults.expired-items-time-minutes", 1440); // Default 24 hours
    }

    public FileConfiguration getConfig() {
        return config;
    }
}

package com.tatayless.sovereignty;

import org.bukkit.plugin.java.JavaPlugin;

public class Sovereignty extends JavaPlugin {
    @Override
    public void onEnable() {
        // Plugin startup logic
        getLogger().info("Sovereignty has been enabled!");
    }

    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Sovereignty has been disabled!");
    }
}

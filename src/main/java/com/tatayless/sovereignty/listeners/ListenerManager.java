package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.plugin.PluginManager;

public class ListenerManager {
    private final Sovereignty plugin;

    public ListenerManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    public void registerListeners() {
        PluginManager pluginManager = plugin.getServer().getPluginManager();

        // Register player listeners
        pluginManager.registerEvents(new PlayerListener(plugin), plugin);

        // Register protection listeners
        pluginManager.registerEvents(new ProtectionListener(plugin), plugin);

        // Register war listeners
        pluginManager.registerEvents(new WarListener(plugin), plugin);

        // Register NPC listeners
        pluginManager.registerEvents(new NPCListener(plugin), plugin);
    }
}

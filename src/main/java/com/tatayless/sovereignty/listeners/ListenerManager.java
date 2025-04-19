package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.services.VaultService;

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

        // Register Vault listeners (This should be the ONLY place it's registered)
        VaultService vaultService = plugin.getServiceManager().getVaultService();
        if (vaultService != null) { // Good practice to check if service retrieval succeeded
            pluginManager.registerEvents(new VaultListener(plugin, vaultService), plugin);
            plugin.getLogger().info("Registered VaultListener."); // Add log confirmation
        } else {
            plugin.getLogger().severe("Failed to register VaultListener: VaultService is null!");
        }
    }
}

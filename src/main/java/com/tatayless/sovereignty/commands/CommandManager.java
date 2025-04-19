package com.tatayless.sovereignty.commands;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.commands.nation.*;
import com.tatayless.sovereignty.commands.vault.VaultCommand;
import com.tatayless.sovereignty.commands.war.WarCommand;
import com.tatayless.sovereignty.listeners.PlayerMoveListener;
import com.tatayless.sovereignty.managers.ToggleManager;
import com.tatayless.sovereignty.commands.admin.AdminCommandExecutor;
import org.bukkit.command.PluginCommand;

public class CommandManager {
    private final Sovereignty plugin;
    private ToggleManager toggleManager;

    public CommandManager(Sovereignty plugin) {
        this.plugin = plugin;
        this.toggleManager = new ToggleManager(plugin);
    }

    public void registerCommands() {
        // Register main nation command
        PluginCommand nationCommand = plugin.getCommand("nation");
        if (nationCommand != null) {
            NationCommandExecutor nationExecutor = new NationCommandExecutor(plugin);
            nationCommand.setExecutor(nationExecutor);
            nationCommand.setTabCompleter(nationExecutor);
        }

        // Register admin command
        PluginCommand adminCommand = plugin.getCommand("nationadmin");
        if (adminCommand != null) {
            AdminCommandExecutor adminExecutor = new AdminCommandExecutor(plugin);
            adminCommand.setExecutor(adminExecutor);
            adminCommand.setTabCompleter(adminExecutor);
        }

        // Register war command
        PluginCommand warCommand = plugin.getCommand("war");
        if (warCommand != null) {
            WarCommand warExecutor = new WarCommand(plugin);
            warCommand.setExecutor(warExecutor);
            warCommand.setTabCompleter(warExecutor);
        }

        // Register vault command
        PluginCommand vaultCommand = plugin.getCommand("vault");
        if (vaultCommand != null) {
            VaultCommand vaultExecutor = new VaultCommand(plugin);
            vaultCommand.setExecutor(vaultExecutor);
            vaultCommand.setTabCompleter(vaultExecutor);
        }

        // Register player movement listener for auto claim/unclaim
        plugin.getServer().getPluginManager().registerEvents(
                new PlayerMoveListener(plugin, toggleManager), plugin);
    }

    public ToggleManager getToggleManager() {
        return toggleManager;
    }
}

package com.tatayless.sovereignty.commands;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.commands.nation.*;
import com.tatayless.sovereignty.commands.vault.VaultCommand;
import com.tatayless.sovereignty.commands.war.WarCommand;
import org.bukkit.command.PluginCommand;

public class CommandManager {
    private final Sovereignty plugin;

    public CommandManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        // Register main nation command
        PluginCommand nationCommand = plugin.getCommand("nation");
        if (nationCommand != null) {
            NationCommandExecutor nationExecutor = new NationCommandExecutor(plugin);
            nationCommand.setExecutor(nationExecutor);
            nationCommand.setTabCompleter(nationExecutor);
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
    }
}

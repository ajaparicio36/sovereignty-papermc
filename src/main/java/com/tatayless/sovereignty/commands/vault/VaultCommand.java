package com.tatayless.sovereignty.commands.vault;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class VaultCommand implements CommandExecutor, TabCompleter {
    private final Sovereignty plugin;

    public VaultCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLocalizationManager().getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        // Check if player is in a nation
        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        // Open vault
        player.sendMessage(plugin.getLocalizationManager().getMessage("vault.opened"));
        plugin.getServiceManager().getVaultService().openVault(player, sovereigntyPlayer.getNationId());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // No tab completion needed for simple vault command
        return Collections.emptyList();
    }
}

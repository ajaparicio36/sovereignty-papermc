package com.tatayless.sovereignty.commands.vault;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VaultCommand implements CommandExecutor, TabCompleter {
    private final Sovereignty plugin;

    public VaultCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLocalizationManager().getComponent("general.player-only"));
            return true;
        }

        Player player = (Player) sender;
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        // Check if player is in a nation
        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
            return true;
        }

        // Check for page argument
        int page = 0;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]) - 1; // Convert to 0-based index
                if (page < 0)
                    page = 0;
            } catch (NumberFormatException e) {
                // Ignore invalid number, use default page 0
                page = 0;
            }
        }

        // Open vault at specified page
        player.sendMessage(plugin.getLocalizationManager().getComponent("vault.opened"));
        plugin.getServiceManager().getVaultService().openVaultPage(player, sovereigntyPlayer.getNationId(), page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Suggest pages 1-10
            return Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10").stream()
                    .filter(s -> s.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

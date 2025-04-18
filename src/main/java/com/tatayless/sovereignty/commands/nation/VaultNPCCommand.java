package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.commands.nation.NationCommandExecutor.SubCommand;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class VaultNPCCommand implements SubCommand {
    private final Sovereignty plugin;

    public VaultNPCCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "vaultnpc";
    }

    @Override
    public String getDescription() {
        return "Creates or removes a Vault NPC";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        // Check if player is in a nation
        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
            return true;
        }

        String nationId = sovereigntyPlayer.getNationId();
        Nation nation = plugin.getServiceManager().getNationService().getNation(nationId);

        // Check if player is an officer (president or senator)
        if (!nation.isOfficer(playerId)) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.no-permission"));
            return true;
        }

        // Handle subcommands (create and remove)
        if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
            // Remove the NPC
            CompletableFuture<Boolean> future = plugin.getServiceManager().getVaultService()
                    .removeVaultNPC(nationId);

            future.thenAccept(success -> {
                if (success) {
                    player.sendMessage(plugin.getLocalizationManager().getComponent("vault.npc-removed"));
                } else {
                    player.sendMessage(plugin.getLocalizationManager().getComponent("vault.npc-removal-failed"));
                }
            });
        } else {
            // Create or move the NPC to the player's location
            CompletableFuture<Boolean> future = plugin.getServiceManager().getVaultService()
                    .createOrMoveVaultNPC(nationId, player.getLocation(), playerId);

            future.thenAccept(success -> {
                if (success) {
                    player.sendMessage(plugin.getLocalizationManager().getComponent("vault.npc-created"));
                } else {
                    player.sendMessage(plugin.getLocalizationManager().getComponent("vault.npc-creation-failed"));
                }
            });
        }

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "remove").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

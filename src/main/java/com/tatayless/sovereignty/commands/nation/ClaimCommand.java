package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.managers.ToggleManager;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ClaimCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;
    private final ToggleManager toggleManager;

    public ClaimCommand(Sovereignty plugin, ToggleManager toggleManager) {
        this.plugin = plugin;
        this.toggleManager = toggleManager;
    }

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public String getDescription() {
        return "Claim the current chunk for your nation or toggle auto-claiming";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        // Check if player is an officer (president or senator)
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-officer"));
            return true;
        }

        // Check if this is a toggle request
        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            boolean isActive = toggleManager.toggleAutoClaim(player);
            if (isActive) {
                player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.enabled"));
            } else {
                player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.disabled"));
            }
            return true;
        }

        // Try to claim the chunk
        plugin.getServiceManager().getChunkService().claimChunk(player, player.getLocation().getChunk());
        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("toggle").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
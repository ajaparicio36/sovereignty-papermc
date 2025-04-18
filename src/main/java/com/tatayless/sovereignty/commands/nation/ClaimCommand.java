package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class ClaimCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public ClaimCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public String getDescription() {
        return "Claim the current chunk for your nation";
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

        // Try to claim the chunk
        plugin.getServiceManager().getChunkService().claimChunk(player, player.getLocation().getChunk());
        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        // No tab completion for chunk claiming
        return Collections.emptyList();
    }
}

package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class DisbandCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public DisbandCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "disband";
    }

    @Override
    public String getDescription() {
        return "Disband your nation";
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

        // Check if player is president
        if (!nation.getPresidentId().equals(playerId)) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-president"));
            return true;
        }

        // Add confirmation
        if (args.length == 0 || !args[0].equalsIgnoreCase("confirm")) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.confirm-disband"));
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.confirm-disband-command"));
            return true;
        }

        // Disband the nation
        plugin.getServiceManager().getNationService().disbandNation(nation.getId(), playerId).thenAccept(success -> {
            if (success) {
                player.sendMessage(plugin.getLocalizationManager().getMessage(
                        "nation.disbanded",
                        "name", nation.getName()));
            } else {
                player.sendMessage(plugin.getLocalizationManager().getMessage("nation.disband-failed"));
            }
        });

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("confirm");
        }
        return Collections.emptyList();
    }
}

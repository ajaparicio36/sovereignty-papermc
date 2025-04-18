package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AllianceProposeCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public AllianceProposeCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "propose";
    }

    @Override
    public String getDescription() {
        return plugin.getLocalizationManager().getMessage("alliance.propose-description",
                "Propose an alliance to another nation");
    }

    @Override
    public boolean execute(Player player, String[] args) {
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService()
                .getPlayer(player.getUniqueId().toString());

        if (sovereigntyPlayer == null || sovereigntyPlayer.getNationId() == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
            return true;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (!nation.isOfficer(player.getUniqueId().toString())) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-officer"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("alliance.propose-usage",
                    "Usage: /nation alliance propose <nation>"));
            return true;
        }

        String targetNationName = args[0];
        Nation targetNation = plugin.getServiceManager().getNationService().getNationByName(targetNationName);
        if (targetNation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-found"));
            return true;
        }

        if (targetNation.getId().equals(nation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("alliance.cannot-ally-self",
                    "You cannot ally with your own nation"));
            return true;
        }

        if (plugin.getServiceManager().getAllianceService().isAllied(nation.getId(), targetNation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("alliance.already-allied",
                    "You are already allied with this nation"));
            return true;
        }

        plugin.getServiceManager().getAllianceService()
                .proposeAlliance(nation.getId(), targetNation.getId(), player.getUniqueId().toString())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getLocalizationManager().getComponent("alliance.request-sent",
                                "nation", targetNationName));
                    } else {
                        player.sendMessage(plugin.getLocalizationManager().getComponent("alliance.request-failed",
                                "Failed to send alliance request"));
                    }
                });

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<Nation> nations = plugin.getServiceManager().getNationService().getAllNations();

            SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService()
                    .getPlayer(player.getUniqueId().toString());
            if (sovereigntyPlayer != null && sovereigntyPlayer.getNationId() != null) {
                // Filter out player's own nation and existing allies
                String playerNationId = sovereigntyPlayer.getNationId();
                return nations.stream()
                        .filter(nation -> !nation.getId().equals(playerNationId))
                        .filter(nation -> !plugin.getServiceManager().getAllianceService()
                                .isAllied(playerNationId, nation.getId()))
                        .map(Nation::getName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }

            return nations.stream()
                    .map(Nation::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}

package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AllianceListCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public AllianceListCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getDescription() {
        return plugin.getLocalizationManager().getMessage("alliance.list-description");
    }

    @Override
    public boolean execute(Player player, String[] args) {
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService()
                .getPlayer(player.getUniqueId().toString());

        if (sovereigntyPlayer == null || sovereigntyPlayer.getNationId() == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.error"));
            return true;
        }

        player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.list-header"));

        // List current alliances
        if (nation.getAlliances().isEmpty()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.no-alliances"));
        } else {
            player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.current-alliances"));
            for (String allyId : nation.getAlliances()) {
                Nation ally = plugin.getServiceManager().getNationService().getNation(allyId);
                if (ally != null) {
                    player.sendMessage("§a- " + ally.getName());
                }
            }
        }

        // List pending requests (only visible to officers)
        if (nation.isOfficer(player.getUniqueId().toString())) {
            Set<String> requests = plugin.getServiceManager().getAllianceService()
                    .getAllianceRequests(nation.getId());

            if (!requests.isEmpty()) {
                player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.pending-requests"));
                for (String requesterId : requests) {
                    Nation requester = plugin.getServiceManager().getNationService().getNation(requesterId);
                    if (requester != null) {
                        player.sendMessage("§e- " + requester.getName());
                    }
                }
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        return new ArrayList<>(); // No tab completion for list command
    }
}

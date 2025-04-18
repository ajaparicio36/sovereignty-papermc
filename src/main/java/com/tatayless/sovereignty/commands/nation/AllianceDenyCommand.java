package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class AllianceDenyCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public AllianceDenyCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "deny";
    }

    @Override
    public String getDescription() {
        return plugin.getLocalizationManager().getMessage("alliance.deny-description", "Deny an alliance request");
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
        if (!nation.isOfficer(player.getUniqueId().toString())) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-officer"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.deny-usage",
                    "Usage: /nation alliance deny <nation>"));
            return true;
        }

        String senderNationName = args[0];
        Nation senderNation = plugin.getServiceManager().getNationService().getNationByName(senderNationName);

        if (senderNation == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-found"));
            return true;
        }

        Set<String> requests = plugin.getServiceManager().getAllianceService()
                .getAllianceRequests(nation.getId());

        if (!requests.contains(senderNation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.no-request-found",
                    "No alliance request found from this nation"));
            return true;
        }

        plugin.getServiceManager().getAllianceService()
                .denyAlliance(nation.getId(), senderNation.getId(), player.getUniqueId().toString())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.request-denied",
                                "nation", senderNationName));
                    } else {
                        player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.deny-failed",
                                "Failed to deny alliance request"));
                    }
                });

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService()
                    .getPlayer(player.getUniqueId().toString());

            if (sovereigntyPlayer != null && sovereigntyPlayer.getNationId() != null) {
                String nationId = sovereigntyPlayer.getNationId();
                Set<String> requestIds = plugin.getServiceManager().getAllianceService()
                        .getAllianceRequests(nationId);

                if (!requestIds.isEmpty()) {
                    String partial = args[0].toLowerCase();
                    return requestIds.stream()
                            .map(id -> plugin.getServiceManager().getNationService().getNation(id))
                            .filter(n -> n != null)
                            .map(Nation::getName)
                            .filter(name -> name.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
            }
        }
        return new ArrayList<>();
    }
}

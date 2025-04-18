package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AllianceBreakCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public AllianceBreakCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "break";
    }

    @Override
    public String getDescription() {
        return plugin.getLocalizationManager().getMessage("alliance.break-description", "Break an existing alliance");
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
            player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.break-usage",
                    "Usage: /nation alliance break <nation>"));
            return true;
        }

        String allyNationName = args[0];
        Nation allyNation = plugin.getServiceManager().getNationService().getNationByName(allyNationName);

        if (allyNation == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-found"));
            return true;
        }

        if (!plugin.getServiceManager().getAllianceService().isAllied(nation.getId(), allyNation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.not-allied",
                    "You are not allied with this nation"));
            return true;
        }

        plugin.getServiceManager().getAllianceService()
                .breakAlliance(nation.getId(), allyNation.getId(), player.getUniqueId().toString())
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.break-success",
                                "nation", allyNationName));
                    } else {
                        player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.break-failed",
                                "Failed to break alliance"));
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
                Nation nation = plugin.getServiceManager().getNationService().getNation(nationId);

                if (nation != null && !nation.getAlliances().isEmpty()) {
                    String partial = args[0].toLowerCase();
                    return nation.getAlliances().stream()
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

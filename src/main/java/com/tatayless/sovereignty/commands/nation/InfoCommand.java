package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.NationService;
import com.tatayless.sovereignty.services.PlayerService;
import org.bukkit.entity.Player;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InfoCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final PlayerService playerService;

    public InfoCommand(Sovereignty plugin) {
        this.plugin = plugin;
        this.nationService = plugin.getServiceManager().getNationService();
        this.playerService = plugin.getServiceManager().getPlayerService();
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "View information about a nation";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        Nation nation;

        if (args.length == 0) {
            // Show player's own nation info
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(player.getUniqueId().toString());
            if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
                return true;
            }

            nation = nationService.getNation(sovereigntyPlayer.getNationId());
            if (nation == null) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
                return true;
            }
        } else {
            // Show info for specified nation
            String nationName = args[0];
            nation = nationService.getNationByName(nationName);

            if (nation == null) {
                player.sendMessage(plugin.getLocalizationManager().getComponent(
                        "nation.nation-not-found",
                        "nation", nationName));
                return true;
            }
        }

        // Get president's name
        String presidentName = "Unknown";
        if (nation.getPresidentId() != null) {
            SovereigntyPlayer president = playerService.getPlayer(nation.getPresidentId());
            if (president != null) {
                presidentName = president.getName();
            }
        }

        // Count citizens
        int totalCitizens = 1 + // President
                nation.getSenators().size() +
                nation.getSoldiers().size() +
                nation.getCitizens().size();

        // Count chunks
        int claimedChunks = nation.getClaimedChunks().size();
        int annexedChunks = nation.getAnnexedChunks().size();

        // Get max chunks based on power level
        int maxChunks = plugin.getConfigManager().getMaxChunksForPowerLevel(nation.getPowerLevel());

        // Get alliances
        String alliances = nation.getAlliances().stream()
                .map(id -> {
                    Nation ally = nationService.getNation(id);
                    return ally != null ? ally.getName() : "Unknown";
                })
                .collect(Collectors.joining(", "));

        if (alliances.isEmpty()) {
            alliances = "None";
        }

        // Send info message
        player.sendMessage(plugin.getLocalizationManager().getComponent(
                "nation.info",
                "name", nation.getName(),
                "power", String.format("%.2f", nation.getPower()),
                "powerLevel", String.valueOf(nation.getPowerLevel()),
                "president", presidentName,
                "citizens", String.valueOf(totalCitizens),
                "chunks", String.valueOf(claimedChunks),
                "maxChunks", String.valueOf(maxChunks),
                "annexedChunks", String.valueOf(annexedChunks),
                "alliances", alliances));

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return nationService.getAllNations().stream()
                    .map(Nation::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

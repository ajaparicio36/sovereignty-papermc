package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class LeaveCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public LeaveCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getDescription() {
        return "Leave your current nation";
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

        // Presidents cannot leave their nation, they must disband it
        if (nation.getPresidentId().equals(playerId)) {
            player.sendMessage("§cAs the president, you cannot leave your nation. Use §f/nation disband §cinstead.");
            return true;
        }

        // Remove player from nation
        String nationName = nation.getName();
        String oldNationId = sovereigntyPlayer.getNationId();

        // Remove from appropriate role list
        if (nation.getSenators().contains(playerId)) {
            nation.removeSenator(playerId);
        }
        if (nation.getSoldiers().contains(playerId)) {
            nation.removeSoldier(playerId);
        }
        if (nation.getCitizens().contains(playerId)) {
            nation.removeCitizen(playerId);
        }

        // Update player
        sovereigntyPlayer.setNationId(null);
        sovereigntyPlayer.setRole(null);

        // Save changes
        plugin.getServiceManager().getPlayerService().updatePlayer(sovereigntyPlayer);
        plugin.getServiceManager().getNationService().saveNation(nation);

        // Send messages
        player.sendMessage(plugin.getLocalizationManager().getMessage(
                "nation.left",
                "name", nationName));

        // Notify other nation members
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            SovereigntyPlayer member = plugin.getServiceManager().getPlayerService()
                    .getPlayer(onlinePlayer.getUniqueId().toString());

            if (member != null && oldNationId.equals(member.getNationId())) {
                onlinePlayer.sendMessage(plugin.getLocalizationManager().getMessage(
                        "nation.player-left",
                        "player", player.getName()));
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        // No tab completion for leaving
        return Collections.emptyList();
    }
}

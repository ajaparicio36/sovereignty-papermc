package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AppointCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public AppointCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "appoint";
    }

    @Override
    public String getDescription() {
        return "Appoint a player to a role in your nation";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/nation appoint <player> <senator|soldier>"));
            return true;
        }

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

        // Find target player
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "nation.player-not-found",
                    "player", targetName));
            return true;
        }

        String targetId = targetPlayer.getUniqueId().toString();
        SovereigntyPlayer targetSovPlayer = plugin.getServiceManager().getPlayerService().getPlayer(targetId);

        if (targetSovPlayer == null || !targetSovPlayer.hasNation() ||
                !targetSovPlayer.getNationId().equals(nation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.player-not-member"));
            return true;
        }

        // Get role to appoint
        String roleArg = args[1].toLowerCase();
        Nation.Role role;

        switch (roleArg) {
            case "senator":
                // Only president can appoint senators
                if (!nation.getPresidentId().equals(playerId)) {
                    player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-president"));
                    return true;
                }
                role = Nation.Role.SENATOR;
                break;

            case "soldier":
                // Officers (presidents and senators) can appoint soldiers
                if (!nation.isOfficer(playerId)) {
                    player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-officer"));
                    return true;
                }
                role = Nation.Role.SOLDIER;
                break;

            default:
                player.sendMessage(plugin.getLocalizationManager().getMessage("nation.invalid-role"));
                return true;
        }

        // Check if player already has this role
        if ((role == Nation.Role.SENATOR && nation.getSenators().contains(targetId)) ||
                (role == Nation.Role.SOLDIER && nation.getSoldiers().contains(targetId))) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.already-has-role"));
            return true;
        }

        // Remove from existing role lists
        if (nation.getSenators().contains(targetId)) {
            nation.removeSenator(targetId);
        }
        if (nation.getSoldiers().contains(targetId)) {
            nation.removeSoldier(targetId);
        }
        if (nation.getCitizens().contains(targetId)) {
            nation.removeCitizen(targetId);
        }

        // Add to new role
        if (role == Nation.Role.SENATOR) {
            nation.addSenator(targetId);
        } else if (role == Nation.Role.SOLDIER) {
            nation.addSoldier(targetId);
            // Set soldier lives based on power level
            int lives = plugin.getConfigManager().getSoldierLivesForPowerLevel(nation.getPowerLevel());
            targetSovPlayer.setSoldierLives(lives);
        }

        // Update player role
        targetSovPlayer.setRole(role);

        // Save changes
        plugin.getServiceManager().getPlayerService().updatePlayer(targetSovPlayer);
        plugin.getServiceManager().getNationService().saveNation(nation);

        // Send messages
        player.sendMessage(plugin.getLocalizationManager().getMessage(
                "nation.appointed",
                "player", targetPlayer.getName(),
                "role", roleArg));

        targetPlayer.sendMessage(plugin.getLocalizationManager().getMessage(
                "nation.been-appointed",
                "role", roleArg));

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            return Collections.emptyList();
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Tab complete nation members
            String input = args[0].toLowerCase();
            List<String> memberNames = new ArrayList<>();

            // Get online players who are members of the nation
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                SovereigntyPlayer sp = plugin.getServiceManager().getPlayerService()
                        .getPlayer(onlinePlayer.getUniqueId().toString());

                if (sp != null && sp.getNationId() != null &&
                        sp.getNationId().equals(nation.getId()) &&
                        !onlinePlayer.getUniqueId().toString().equals(playerId)) {

                    memberNames.add(onlinePlayer.getName());
                }
            }

            return memberNames.stream()
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Tab complete roles based on permissions
            String input = args[1].toLowerCase();
            List<String> roles = new ArrayList<>();

            if (nation.getPresidentId().equals(playerId)) {
                // President can appoint senators
                roles.add("senator");
            }

            if (nation.isOfficer(playerId)) {
                // Officers can appoint soldiers
                roles.add("soldier");
            }

            return roles.stream()
                    .filter(role -> role.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

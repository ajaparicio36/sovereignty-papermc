package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JoinCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;

    public JoinCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public String getDescription() {
        return "Join a nation you've been invited to";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/nation join <nation>"));
            return true;
        }

        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        // Create player if not exists
        if (sovereigntyPlayer == null) {
            sovereigntyPlayer = plugin.getServiceManager().getPlayerService().createPlayer(player);
        }

        // Check if player is already in a nation
        if (sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.already-in-nation"));
            return true;
        }

        // Find the target nation
        String nationName = args[0];
        Nation nation = plugin.getServiceManager().getNationService().getNationByName(nationName);

        if (nation == null) {
            player.sendMessage("§cNation not found: " + nationName);
            return true;
        }

        // Check if player has an invitation
        InviteCommand inviteCommand = null;
        for (NationCommandExecutor.SubCommand cmd : ((NationCommandExecutor) plugin.getCommand("nation").getExecutor())
                .getSubCommands()) {
            if (cmd instanceof InviteCommand) {
                inviteCommand = (InviteCommand) cmd;
                break;
            }
        }

        if (inviteCommand == null || !inviteCommand.hasInvite(player.getUniqueId(), nation.getId())) {
            player.sendMessage("§cYou don't have an invitation to join this nation.");
            return true;
        }

        // Add player to nation as citizen
        sovereigntyPlayer.setNationId(nation.getId());
        sovereigntyPlayer.setRole(Nation.Role.CITIZEN);
        nation.addCitizen(playerId);

        // Save changes
        plugin.getServiceManager().getPlayerService().updatePlayer(sovereigntyPlayer);
        plugin.getServiceManager().getNationService().saveNation(nation);

        // Remove invitation
        inviteCommand.removeInvite(player.getUniqueId());

        // Send messages
        player.sendMessage(plugin.getLocalizationManager().getMessage(
                "nation.joined",
                "name", nation.getName()));

        // Notify other nation members
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            SovereigntyPlayer member = plugin.getServiceManager().getPlayerService()
                    .getPlayer(onlinePlayer.getUniqueId().toString());

            if (member != null && member.getNationId() != null &&
                    member.getNationId().equals(nation.getId()) &&
                    !member.getId().equals(playerId)) {

                onlinePlayer.sendMessage(plugin.getLocalizationManager().getMessage(
                        "nation.player-joined",
                        "player", player.getName()));
            }
        }

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return plugin.getServiceManager().getNationService().getAllNations().stream()
                    .map(Nation::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

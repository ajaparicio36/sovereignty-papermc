package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class InviteCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;
    private final Map<UUID, NationInvite> pendingInvites = new HashMap<>();

    public InviteCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "invite";
    }

    @Override
    public String getDescription() {
        return "Invite a player to your nation";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/nation invite <player>"));
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

        // Check if player is an officer (president or senator)
        if (!nation.isOfficer(playerId)) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-officer"));
            return true;
        }

        // Find the target player
        String targetName = args[0];
        Player targetPlayer = Bukkit.getPlayer(targetName);

        if (targetPlayer == null || !targetPlayer.isOnline()) {
            player.sendMessage("§cPlayer not found or offline: " + targetName);
            return true;
        }

        // Check if target is already in a nation
        SovereigntyPlayer targetSovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(
                targetPlayer.getUniqueId().toString());

        if (targetSovereigntyPlayer != null && targetSovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.player-already-in-nation"));
            return true;
        }

        // Create invitation
        UUID targetUuid = targetPlayer.getUniqueId();
        NationInvite invite = new NationInvite(nation.getId(), System.currentTimeMillis() + 60000); // 1 minute
                                                                                                    // expiration
        pendingInvites.put(targetUuid, invite);

        // Send messages
        player.sendMessage(
                plugin.getLocalizationManager().getComponent("nation.invite.sent", "player", targetPlayer.getName()));
        targetPlayer.sendMessage(
                plugin.getLocalizationManager().getComponent("nation.invite.received", "nation", nation.getName()));
        targetPlayer.sendMessage(plugin.getLocalizationManager().getComponent("nation.invite.join-instructions",
                "nation", nation.getName()));
        targetPlayer.sendMessage(plugin.getLocalizationManager().getComponent("nation.invite.expiration"));

        // Schedule expiration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            NationInvite storedInvite = pendingInvites.get(targetUuid);
            if (storedInvite != null && storedInvite.equals(invite)) {
                pendingInvites.remove(targetUuid);
                Player target = Bukkit.getPlayer(targetUuid);
                if (target != null && target.isOnline()) {
                    target.sendMessage(plugin.getLocalizationManager().getComponent("nation.invite.expired", "nation",
                            nation.getName()));
                }
            }
        }, 1200); // 60 seconds * 20 ticks

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player)) // Exclude the command sender
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public boolean hasInvite(UUID playerUuid, String nationId) {
        NationInvite invite = pendingInvites.get(playerUuid);
        return invite != null && invite.nationId.equals(nationId) && invite.expiration > System.currentTimeMillis();
    }

    public void removeInvite(UUID playerUuid) {
        pendingInvites.remove(playerUuid);
    }

    private static class NationInvite {
        private final String nationId;
        private final long expiration;

        public NationInvite(String nationId, long expiration) {
            this.nationId = nationId;
            this.expiration = expiration;
        }
    }
}

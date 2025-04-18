package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.managers.ToggleManager;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerMoveListener implements Listener {
    private final Sovereignty plugin;
    private final ToggleManager toggleManager;

    public PlayerMoveListener(Sovereignty plugin, ToggleManager toggleManager) {
        this.plugin = plugin;
        this.toggleManager = toggleManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Check if player moved to a new chunk
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        if (fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ()) {
            return; // Player didn't change chunks
        }

        // Check for auto-claim
        if (toggleManager.isAutoClaimEnabled(player)) {
            handleAutoClaim(player, toChunk);
        }
        // Check for auto-unclaim
        else if (toggleManager.isAutoUnclaimEnabled(player)) {
            handleAutoUnclaim(player, toChunk);
        }
    }

    private void handleAutoClaim(Player player, Chunk chunk) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-claim has been disabled because you are not in a nation.");
            return;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-claim has been disabled because you are not in a nation.");
            return;
        }

        // Check if player is an officer
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-claim has been disabled because you are not an officer.");
            return;
        }

        // Check if we've reached the max claims
        if (toggleManager.hasReachedMaxClaims(player)) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-claim has been disabled because you reached the maximum of " +
                    toggleManager.getMaxAutoClaims() + " chunks.");
            return;
        }

        // Try to claim the chunk - fix CompletableFuture handling
        plugin.getServiceManager().getChunkService().claimChunk(player, chunk).thenAccept(success -> {
            if (success) {
                int claimed = toggleManager.incrementChunksClaimed(player);
                player.sendMessage("§a[Auto-claim] Chunk claimed (" + claimed + "/" +
                        toggleManager.getMaxAutoClaims() + ")");
            }
        });
    }

    private void handleAutoUnclaim(Player player, Chunk chunk) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-unclaim has been disabled because you are not in a nation.");
            return;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-unclaim has been disabled because you are not in a nation.");
            return;
        }

        // Check if player is an officer
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            toggleManager.resetToggles(player);
            player.sendMessage("§cAuto-unclaim has been disabled because you are not an officer.");
            return;
        }

        // Try to unclaim the chunk - fix CompletableFuture handling
        plugin.getServiceManager().getChunkService().unclaimChunk(player, chunk).thenAccept(success -> {
            if (success) {
                player.sendMessage("§a[Auto-unclaim] Chunk unclaimed");
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Reset toggles when player leaves
        toggleManager.resetToggles(event.getPlayer());
    }
}

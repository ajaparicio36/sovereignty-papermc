package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.managers.ToggleManager;
import com.tatayless.sovereignty.models.ChunkLocation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerMoveListener implements Listener {
    private final Sovereignty plugin;
    private final ToggleManager toggleManager;
    private final Map<UUID, ChunkLocation> lastPlayerChunks = new HashMap<>();

    public PlayerMoveListener(Sovereignty plugin, ToggleManager toggleManager) {
        this.plugin = plugin;
        this.toggleManager = toggleManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Skip if player moved within the same block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        // Check if player moved to a new chunk
        if (fromChunk.getX() != toChunk.getX() || fromChunk.getZ() != toChunk.getZ()) {
            // Handle territory notification
            handleTerritoryNotification(player, toChunk);

            // Handle auto-claim if enabled
            if (toggleManager.isAutoClaimEnabled(player)) {
                handleAutoClaim(player, toChunk);
            }

            // Handle auto-unclaim if enabled
            if (toggleManager.isAutoUnclaimEnabled(player)) {
                handleAutoUnclaim(player, toChunk);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up cached data when player leaves
        lastPlayerChunks.remove(player.getUniqueId());
        toggleManager.resetToggles(player);
    }

    private void handleTerritoryNotification(Player player, Chunk chunk) {
        UUID playerId = player.getUniqueId();
        ChunkLocation currentChunkLoc = new ChunkLocation(chunk);
        ChunkLocation previousChunkLoc = lastPlayerChunks.get(playerId);

        // Update the player's last known chunk
        lastPlayerChunks.put(playerId, currentChunkLoc);

        // Get owner of current chunk
        Nation currentChunkOwner = plugin.getServiceManager().getNationService().getNationByChunk(currentChunkLoc);

        // Get owner of previous chunk
        Nation previousChunkOwner = null;
        if (previousChunkLoc != null) {
            previousChunkOwner = plugin.getServiceManager().getNationService().getNationByChunk(previousChunkLoc);
        }

        // Only send message if ownership changed
        if (currentChunkOwner != previousChunkOwner) {
            if (currentChunkOwner != null) {
                // Entering a nation's territory
                player.sendMessage(plugin.getLocalizationManager().getMessage(
                        "chunk.entered",
                        "name", currentChunkOwner.getName()));
            } else if (previousChunkOwner != null) {
                // Exiting a nation's territory
                player.sendMessage(plugin.getLocalizationManager().getMessage(
                        "chunk.exited",
                        "name", previousChunkOwner.getName()));
            }
        }
    }

    private void handleAutoClaim(Player player, Chunk chunk) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.not-in-nation"));
            return;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.not-in-nation"));
            return;
        }

        // Check if player is an officer
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.not-officer"));
            return;
        }

        // Check if we've reached the max claims
        if (toggleManager.hasReachedMaxClaims(player)) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.max-reached",
                    "max", String.valueOf(toggleManager.getMaxAutoClaims())));
            return;
        }

        // Try to claim the chunk - fix CompletableFuture handling
        plugin.getServiceManager().getChunkService().claimChunk(player, chunk).thenAccept(success -> {
            if (success) {
                int claimed = toggleManager.incrementChunksClaimed(player);
                player.sendMessage(plugin.getLocalizationManager().getMessage("auto-claim.chunk-claimed",
                        "current", String.valueOf(claimed),
                        "max", String.valueOf(toggleManager.getMaxAutoClaims())));
            }
        });
    }

    private void handleAutoUnclaim(Player player, Chunk chunk) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-unclaim.not-in-nation"));
            return;
        }

        Nation nation = plugin.getServiceManager().getNationService().getNation(sovereigntyPlayer.getNationId());
        if (nation == null) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-unclaim.not-in-nation"));
            return;
        }

        // Check if player is an officer
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            toggleManager.resetToggles(player);
            player.sendMessage(plugin.getLocalizationManager().getMessage("auto-unclaim.not-officer"));
            return;
        }

        // Try to unclaim the chunk - fix CompletableFuture handling
        plugin.getServiceManager().getChunkService().unclaimChunk(player, chunk).thenAccept(success -> {
            if (success) {
                player.sendMessage(plugin.getLocalizationManager().getMessage("auto-unclaim.chunk-unclaimed"));
            }
        });
    }
}

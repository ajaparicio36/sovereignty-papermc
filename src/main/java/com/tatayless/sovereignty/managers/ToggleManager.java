package com.tatayless.sovereignty.managers;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ToggleManager {
    @SuppressWarnings("unused")
    private final Sovereignty plugin;
    private final Map<UUID, Boolean> autoClaimEnabled = new HashMap<>();
    private final Map<UUID, Boolean> autoUnclaimEnabled = new HashMap<>();
    private final Map<UUID, Integer> chunksClaimed = new HashMap<>();

    private final int MAX_AUTO_CLAIMS = 50; // Maximum chunks that can be claimed in one toggle session

    public ToggleManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    /**
     * Toggle auto-claim for a player
     * 
     * @param player The player
     * @return Whether auto-claim is now enabled
     */
    public boolean toggleAutoClaim(Player player) {
        UUID playerId = player.getUniqueId();

        // If player has auto-unclaim active, disable it first
        if (isAutoUnclaimEnabled(player)) {
            autoUnclaimEnabled.put(playerId, false);
        }

        boolean current = autoClaimEnabled.getOrDefault(playerId, false);
        autoClaimEnabled.put(playerId, !current);

        // Reset chunk counter when toggling on
        if (!current) {
            chunksClaimed.put(playerId, 0);
        }

        return !current;
    }

    /**
     * Toggle auto-unclaim for a player
     * 
     * @param player The player
     * @return Whether auto-unclaim is now enabled
     */
    public boolean toggleAutoUnclaim(Player player) {
        UUID playerId = player.getUniqueId();

        // If player has auto-claim active, disable it first
        if (isAutoClaimEnabled(player)) {
            autoClaimEnabled.put(playerId, false);
        }

        boolean current = autoUnclaimEnabled.getOrDefault(playerId, false);
        autoUnclaimEnabled.put(playerId, !current);
        return !current;
    }

    /**
     * Check if auto-claim is enabled for a player
     * 
     * @param player The player
     * @return Whether auto-claim is enabled
     */
    public boolean isAutoClaimEnabled(Player player) {
        return autoClaimEnabled.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Check if auto-unclaim is enabled for a player
     * 
     * @param player The player
     * @return Whether auto-unclaim is enabled
     */
    public boolean isAutoUnclaimEnabled(Player player) {
        return autoUnclaimEnabled.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Increment the count of chunks claimed
     * 
     * @param player The player
     * @return The new count of chunks claimed
     */
    public int incrementChunksClaimed(Player player) {
        UUID playerId = player.getUniqueId();
        int count = chunksClaimed.getOrDefault(playerId, 0) + 1;
        chunksClaimed.put(playerId, count);
        return count;
    }

    /**
     * Get the count of chunks claimed in the current toggle session
     * 
     * @param player The player
     * @return The count of chunks claimed
     */
    public int getChunksClaimed(Player player) {
        return chunksClaimed.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Check if player has reached maximum claims
     * 
     * @param player The player
     * @return Whether player has reached max claims
     */
    public boolean hasReachedMaxClaims(Player player) {
        return getChunksClaimed(player) >= MAX_AUTO_CLAIMS;
    }

    /**
     * Get the maximum number of chunks that can be auto-claimed
     * 
     * @return The maximum number of chunks
     */
    public int getMaxAutoClaims() {
        return MAX_AUTO_CLAIMS;
    }

    /**
     * Reset toggle states for a player
     * 
     * @param player The player
     */
    public void resetToggles(Player player) {
        UUID playerId = player.getUniqueId();
        autoClaimEnabled.put(playerId, false);
        autoUnclaimEnabled.put(playerId, false);
        chunksClaimed.remove(playerId);
    }
}

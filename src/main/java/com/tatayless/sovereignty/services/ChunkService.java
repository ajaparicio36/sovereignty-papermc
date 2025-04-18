package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.ChunkLocation;
import com.tatayless.sovereignty.models.Nation;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class ChunkService {
    private final Sovereignty plugin;
    private final NationService nationService;

    public ChunkService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
    }

    public CompletableFuture<Boolean> claimChunk(Player player, Chunk chunk) {
        String playerId = player.getUniqueId().toString();
        Nation nation = nationService.getPlayerNation(playerId);

        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has permission to claim
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-officer"));
            return CompletableFuture.completedFuture(false);
        }

        // Check if chunk is already claimed
        ChunkLocation chunkLocation = new ChunkLocation(chunk);
        if (nationService.isChunkClaimed(chunkLocation)) {
            Nation owner = nationService.getNationByChunk(chunkLocation);
            player.sendMessage(plugin.getLocalizationManager().getComponent(
                    "chunk.already-claimed",
                    "nation", owner.getName()));
            return CompletableFuture.completedFuture(false);
        }

        // Check if nation has reached chunk limit
        int maxChunks = plugin.getConfigManager().getMaxChunksForPowerLevel(nation.getPowerLevel());
        if (nation.getClaimedChunks().size() >= maxChunks && !player.hasPermission("sovereignty.admin.chunks")) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("chunk.max-reached"));
            return CompletableFuture.completedFuture(false);
        }

        // All checks passed, claim the chunk
        return nationService.claimChunk(nation.getId(), chunkLocation).thenApply(success -> {
            if (success) {
                player.sendMessage(plugin.getLocalizationManager().getComponent(
                        "chunk.claimed",
                        "nation", nation.getName()));
            }
            return success;
        });
    }

    public CompletableFuture<Boolean> unclaimChunk(Player player, Chunk chunk) {
        String playerId = player.getUniqueId().toString();
        Nation nation = nationService.getPlayerNation(playerId);

        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has permission to unclaim
        if (!nation.isOfficer(playerId) && !player.hasPermission("sovereignty.admin.bypass")) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-officer"));
            return CompletableFuture.completedFuture(false);
        }

        // Check if chunk belongs to the nation
        ChunkLocation chunkLocation = new ChunkLocation(chunk);
        if (!nationService.isChunkClaimed(chunkLocation)) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("chunk.not-claimed"));
            return CompletableFuture.completedFuture(false);
        }

        Nation owner = nationService.getNationByChunk(chunkLocation);
        if (!owner.getId().equals(nation.getId()) && !player.hasPermission("sovereignty.admin.bypass")) {
            player.sendMessage(plugin.getLocalizationManager().getComponent(
                    "chunk.already-claimed",
                    "nation", owner.getName()));
            return CompletableFuture.completedFuture(false);
        }

        // All checks passed, unclaim the chunk
        return nationService.unclaimChunk(nation.getId(), chunkLocation).thenApply(success -> {
            if (success) {
                player.sendMessage(plugin.getLocalizationManager().getComponent(
                        "chunk.unclaimed",
                        "nation", nation.getName()));
            }
            return success;
        });
    }

    public String getChunkInfo(Chunk chunk) {
        ChunkLocation chunkLocation = new ChunkLocation(chunk);
        if (nationService.isChunkClaimed(chunkLocation)) {
            Nation owner = nationService.getNationByChunk(chunkLocation);
            return plugin.getLocalizationManager().getMessage(
                    "chunk.info",
                    "nation", owner.getName());
        } else {
            return plugin.getLocalizationManager().getMessage("chunk.not-claimed-any");
        }
    }

    public CompletableFuture<Boolean> adminClearChunk(Chunk chunk) {
        ChunkLocation chunkLocation = new ChunkLocation(chunk);

        if (!nationService.isChunkClaimed(chunkLocation)) {
            return CompletableFuture.completedFuture(false);
        }

        Nation owner = nationService.getNationByChunk(chunkLocation);
        return nationService.unclaimChunk(owner.getId(), chunkLocation);
    }
}

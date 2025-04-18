package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.ChunkLocation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class WarService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final PlayerService playerService;
    private final Map<String, War> activeWars = new HashMap<>();
    private final Map<ChunkLocation, String> chunkOwners = new HashMap<>();

    public WarService(Sovereignty plugin, NationService nationService, PlayerService playerService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.playerService = playerService;
    }

    public void loadWars() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                Result<Record> results = context.select().from("wars")
                        .where(DSL.field("status").eq("active"))
                        .fetch();

                for (Record record : results) {
                    String id = record.get("id", String.class);
                    String attackerNationId = record.get("attacker_nation_id", String.class);
                    String defenderNationId = record.get("defender_nation_id", String.class);
                    int attackerKills = record.get("attacker_kills", Integer.class);
                    int defenderKills = record.get("defender_kills", Integer.class);
                    int requiredKills = record.get("required_kills", Integer.class);

                    Nation attackerNation = nationService.getNation(attackerNationId);
                    Nation defenderNation = nationService.getNation(defenderNationId);

                    if (attackerNation != null && defenderNation != null) {
                        War war = new War(id, attackerNationId, defenderNationId, requiredKills);
                        war.setAttackerKills(attackerKills);
                        war.setDefenderKills(defenderKills);

                        activeWars.put(id, war);

                        // Update nations with war reference
                        attackerNation.addWar(id);
                        defenderNation.addWar(id);
                    }
                }

                plugin.getLogger().info("Loaded " + activeWars.size() + " active wars from database");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load wars: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<War> declareWar(String attackerNationId, String defenderNationId) {
        Nation attackerNation = nationService.getNation(attackerNationId);
        Nation defenderNation = nationService.getNation(defenderNationId);

        if (attackerNation == null || defenderNation == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if nations are already at war
        if (isAtWar(attackerNationId, defenderNationId)) {
            return CompletableFuture.completedFuture(null);
        }

        // Calculate required kills based on defender's power level
        int requiredKills = defenderNation.getPowerLevel() * 5; // Example scaling

        // Generate war ID
        String warId = UUID.randomUUID().toString();
        War war = new War(warId, attackerNationId, defenderNationId, requiredKills);

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<War>() {
                @Override
                public War execute(Connection connection, DSLContext context) throws SQLException {
                    // Insert war record
                    context.insertInto(
                            DSL.table("wars"),
                            DSL.field("id"),
                            DSL.field("attacker_nation_id"),
                            DSL.field("defender_nation_id"),
                            DSL.field("required_kills")).values(
                                    warId,
                                    attackerNationId,
                                    defenderNationId,
                                    requiredKills)
                            .execute();

                    // Update nations
                    attackerNation.addWar(warId);
                    defenderNation.addWar(warId);

                    nationService.saveNation(attackerNation);
                    nationService.saveNation(defenderNation);

                    activeWars.put(warId, war);

                    // Notify players
                    notifyNationPlayers(attackerNationId,
                            plugin.getLocalizationManager().getComponent("war.declared", "nation",
                                    defenderNation.getName()));

                    notifyNationPlayers(defenderNationId,
                            plugin.getLocalizationManager().getComponent("war.received", "nation",
                                    attackerNation.getName()));

                    return war;
                }
            });
        });
    }

    public CompletableFuture<Boolean> endWar(String warId, String winnerId) {
        War war = activeWars.get(warId);
        if (war == null) {
            return CompletableFuture.completedFuture(false);
        }

        Nation attackerNation = nationService.getNation(war.getAttackerNationId());
        Nation defenderNation = nationService.getNation(war.getDefenderNationId());
        Nation winner = nationService.getNation(winnerId);
        Nation loser = winnerId.equals(attackerNation.getId()) ? defenderNation : attackerNation;

        if (attackerNation == null || defenderNation == null || winner == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Update war record
                context.update(DSL.table("wars"))
                        .set(DSL.field("status"), "ended")
                        .set(DSL.field("winner_id"), winnerId)
                        .set(DSL.field("ended_at"), new Timestamp(System.currentTimeMillis()))
                        .where(DSL.field("id").eq(warId))
                        .execute();

                // Remove war from nations
                attackerNation.removeWar(warId);
                defenderNation.removeWar(warId);

                // Update Power for winner
                if (winner.getId().equals(attackerNation.getId())) {
                    attackerNation.setPower(attackerNation.getPower() + 1.0);
                } else {
                    defenderNation.setPower(defenderNation.getPower() + 1.0);
                }

                // Annex chunks from the loser
                annexChunks(winner, loser);

                nationService.saveNation(attackerNation);
                nationService.saveNation(defenderNation);

                activeWars.remove(warId);

                // Notify players
                String winnerName = winner.getName();
                Component message = plugin.getLocalizationManager().getComponent(
                        "war.ended",
                        "attacker", attackerNation.getName(),
                        "defender", defenderNation.getName(),
                        "winner", winnerName);

                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to end war: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Annexes a portion of the loser's chunks to the winner's territory
     * The annexed chunks will form a contiguous region where possible
     * 
     * @param winner The nation that won the war
     * @param loser  The nation that lost the war
     */
    private void annexChunks(Nation winner, Nation loser) {
        Set<ChunkLocation> loserChunks = new HashSet<>(loser.getClaimedChunks());
        if (loserChunks.isEmpty()) {
            return; // No chunks to annex
        }

        // Calculate how many chunks to annex
        int annexCount = Math.max(1, (int) (loserChunks.size() * plugin.getConfigManager().getAnnexationPercentage()));
        annexCount = Math.min(annexCount, loserChunks.size()); // Don't try to annex more chunks than available

        // Find a starting chunk that is adjacent to winner territory if possible
        ChunkLocation startingChunk = findStartingChunk(winner, loserChunks);
        if (startingChunk == null) {
            // If no adjacent chunk found, just pick the first one
            startingChunk = loserChunks.iterator().next();
        }

        // Use BFS to find contiguous chunks to annex
        Set<ChunkLocation> chunksToAnnex = findContiguousChunks(startingChunk, loserChunks, annexCount);

        // Annex the selected chunks
        int annexed = 0;
        for (ChunkLocation chunk : chunksToAnnex) {
            // Remove chunk from loser's claimed chunks
            loser.removeClaimedChunk(chunk);

            // Add to winner's annexed chunks
            winner.addAnnexedChunk(chunk);

            // Update chunk owner in the chunk map
            chunkOwners.put(chunk, winner.getId());

            annexed++;
        }

        // Notify players about annexation
        if (annexed > 0) {
            Component message = plugin.getLocalizationManager().getComponent(
                    "war.chunks-annexed",
                    "winner", winner.getName(),
                    "loser", loser.getName(),
                    "count", String.valueOf(annexed));

            notifyNationPlayers(winner.getId(), message);
            notifyNationPlayers(loser.getId(), message);

            plugin.getLogger().info(annexed + " chunks annexed from " + loser.getName() + " to " + winner.getName());
        }
    }

    /**
     * Find a good starting chunk for annexation that borders the winner's territory
     * 
     * @param winner      The winning nation
     * @param loserChunks The set of chunks owned by the loser
     * @return A chunk location to start annexation from, or null if none found
     */
    private ChunkLocation findStartingChunk(Nation winner, Set<ChunkLocation> loserChunks) {
        // Combine winner's claimed and annexed chunks
        Set<ChunkLocation> winnerTerritory = new HashSet<>();
        winnerTerritory.addAll(winner.getClaimedChunks());
        winnerTerritory.addAll(winner.getAnnexedChunks());

        // Look for a loser chunk that is adjacent to winner territory
        for (ChunkLocation loserChunk : loserChunks) {
            List<ChunkLocation> adjacentChunks = getAdjacentChunks(loserChunk);
            for (ChunkLocation adjacentChunk : adjacentChunks) {
                if (winnerTerritory.contains(adjacentChunk)) {
                    return loserChunk; // Found a chunk that borders winner territory
                }
            }
        }

        return null; // No good starting point found
    }

    /**
     * Find a contiguous set of chunks to annex
     * 
     * @param startingChunk   The chunk to start from
     * @param availableChunks All available chunks that can be annexed
     * @param maxChunks       Maximum number of chunks to annex
     * @return A set of contiguous chunks to annex
     */
    private Set<ChunkLocation> findContiguousChunks(ChunkLocation startingChunk, Set<ChunkLocation> availableChunks,
            int maxChunks) {
        Set<ChunkLocation> chunksToAnnex = new HashSet<>();
        Queue<ChunkLocation> queue = new LinkedList<>();

        // Start with the initial chunk
        queue.add(startingChunk);
        chunksToAnnex.add(startingChunk);

        while (!queue.isEmpty() && chunksToAnnex.size() < maxChunks) {
            ChunkLocation current = queue.poll();

            // Get all adjacent chunks
            List<ChunkLocation> adjacentChunks = getAdjacentChunks(current);

            // Add all valid adjacent chunks to the queue
            for (ChunkLocation adjacent : adjacentChunks) {
                if (availableChunks.contains(adjacent) && !chunksToAnnex.contains(adjacent)) {
                    queue.add(adjacent);
                    chunksToAnnex.add(adjacent);

                    // Stop if we've reached the target number of chunks
                    if (chunksToAnnex.size() >= maxChunks) {
                        break;
                    }
                }
            }
        }

        // If we couldn't get enough contiguous chunks, add more non-contiguous ones to
        // reach the target
        if (chunksToAnnex.size() < maxChunks) {
            for (ChunkLocation chunk : availableChunks) {
                if (!chunksToAnnex.contains(chunk)) {
                    chunksToAnnex.add(chunk);
                    if (chunksToAnnex.size() >= maxChunks) {
                        break;
                    }
                }
            }
        }

        return chunksToAnnex;
    }

    /**
     * Get all chunks adjacent to the given chunk
     * 
     * @param chunk The chunk to find adjacents for
     * @return List of adjacent chunk locations
     */
    private List<ChunkLocation> getAdjacentChunks(ChunkLocation chunk) {
        List<ChunkLocation> adjacentChunks = new ArrayList<>(4);

        // North
        adjacentChunks.add(new ChunkLocation(chunk.getX(), chunk.getZ() - 1, chunk.getWorldName()));
        // South
        adjacentChunks.add(new ChunkLocation(chunk.getX(), chunk.getZ() + 1, chunk.getWorldName()));
        // East
        adjacentChunks.add(new ChunkLocation(chunk.getX() + 1, chunk.getZ(), chunk.getWorldName()));
        // West
        adjacentChunks.add(new ChunkLocation(chunk.getX() - 1, chunk.getZ(), chunk.getWorldName()));

        return adjacentChunks;
    }

    public CompletableFuture<Boolean> cancelWar(String warId) {
        War war = activeWars.get(warId);
        if (war == null) {
            return CompletableFuture.completedFuture(false);
        }

        Nation attackerNation = nationService.getNation(war.getAttackerNationId());
        Nation defenderNation = nationService.getNation(war.getDefenderNationId());

        if (attackerNation == null || defenderNation == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Update war record
                context.update(DSL.table("wars"))
                        .set(DSL.field("status"), "cancelled")
                        .set(DSL.field("ended_at"), new Timestamp(System.currentTimeMillis()))
                        .where(DSL.field("id").eq(warId))
                        .execute();

                // Remove war from nations
                attackerNation.removeWar(warId);
                defenderNation.removeWar(warId);

                nationService.saveNation(attackerNation);
                nationService.saveNation(defenderNation);

                activeWars.remove(warId);

                // Notify players
                Component message = plugin.getLocalizationManager().getComponent("war.cancelled");

                // Update both notify calls to handle the Component
                for (SovereigntyPlayer player : playerService.getPlayersByNation(attackerNation.getId())) {
                    Player bukkitPlayer = Bukkit.getPlayer(UUID.fromString(player.getId()));
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        bukkitPlayer.sendMessage(message);
                    }
                }

                for (SovereigntyPlayer player : playerService.getPlayersByNation(defenderNation.getId())) {
                    Player bukkitPlayer = Bukkit.getPlayer(UUID.fromString(player.getId()));
                    if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                        bukkitPlayer.sendMessage(message);
                    }
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to cancel war: " + e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> recordKill(String playerId, String victimId) {
        SovereigntyPlayer player = playerService.getPlayer(playerId);
        SovereigntyPlayer victim = playerService.getPlayer(victimId);

        if (player == null || victim == null ||
                !player.hasNation() || !victim.hasNation() ||
                player.getNationId().equals(victim.getNationId())) {
            return CompletableFuture.completedFuture(false);
        }

        // Find war between nations
        War war = getWarBetweenNations(player.getNationId(), victim.getNationId());
        if (war == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Update kill count and check for win condition
        boolean isAttackerKill = player.getNationId().equals(war.getAttackerNationId());

        if (isAttackerKill) {
            war.incrementAttackerKills();
        } else {
            war.incrementDefenderKills();
        }

        // Handle special assassination case
        boolean isAssassination = plugin.getConfigManager().isAssassinationModeEnabled() && victim.isPresident();

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Update war record
                context.update(DSL.table("wars"))
                        .set(
                                DSL.field(isAttackerKill ? "attacker_kills" : "defender_kills"),
                                isAttackerKill ? war.getAttackerKills() : war.getDefenderKills())
                        .where(DSL.field("id").eq(war.getId()))
                        .execute();

                // Reduce victim's soldier lives if they're a soldier
                if (victim.isSoldier()) {
                    victim.setSoldierLives(victim.getSoldierLives() - 1);
                    playerService.updatePlayer(victim);
                }

                // Check win conditions
                String winnerId = null;

                if (isAssassination) {
                    // President assassination = instant win
                    winnerId = player.getNationId();

                    // Broadcast assassination message
                    String nationName = nationService.getNation(victim.getNationId()).getName();
                    Component message = plugin.getLocalizationManager().getComponent(
                            "war.president-killed",
                            "nation", nationName);
                    Bukkit.getServer().broadcast(message);
                } else if (isAttackerKill && war.getAttackerKills() >= war.getRequiredKills()) {
                    winnerId = war.getAttackerNationId();
                } else if (!isAttackerKill && war.getDefenderKills() >= war.getRequiredKills()) {
                    winnerId = war.getDefenderNationId();
                }

                // Broadcast kill
                @SuppressWarnings("unused")
                Nation playerNation = nationService.getNation(player.getNationId());
                Component killMessage = plugin.getLocalizationManager().getComponent(
                        "war.player-killed",
                        "player", victim.getName(),
                        "nation", nationService.getNation(victim.getNationId()).getName(),
                        "current",
                        isAttackerKill ? String.valueOf(war.getAttackerKills())
                                : String.valueOf(war.getDefenderKills()),
                        "required", String.valueOf(war.getRequiredKills()));

                notifyNationPlayers(player.getNationId(), killMessage);
                notifyNationPlayers(victim.getNationId(), killMessage);

                // End war if won
                if (winnerId != null) {
                    endWar(war.getId(), winnerId);
                }

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to record kill: " + e.getMessage());
                return false;
            }
        });
    }

    public boolean isAtWar(String nationId1, String nationId2) {
        for (War war : activeWars.values()) {
            if ((war.getAttackerNationId().equals(nationId1) && war.getDefenderNationId().equals(nationId2)) ||
                    (war.getAttackerNationId().equals(nationId2) && war.getDefenderNationId().equals(nationId1))) {
                return true;
            }
        }
        return false;
    }

    public War getWarBetweenNations(String nationId1, String nationId2) {
        for (War war : activeWars.values()) {
            if ((war.getAttackerNationId().equals(nationId1) && war.getDefenderNationId().equals(nationId2)) ||
                    (war.getAttackerNationId().equals(nationId2) && war.getDefenderNationId().equals(nationId1))) {
                return war;
            }
        }
        return null;
    }

    public List<War> getActiveWars() {
        return new ArrayList<>(activeWars.values());
    }

    public List<War> getNationWars(String nationId) {
        List<War> wars = new ArrayList<>();
        for (War war : activeWars.values()) {
            if (war.getAttackerNationId().equals(nationId) || war.getDefenderNationId().equals(nationId)) {
                wars.add(war);
            }
        }
        return wars;
    }

    private void notifyNationPlayers(String nationId, Component message) {
        List<SovereigntyPlayer> players = playerService.getPlayersByNation(nationId);
        for (SovereigntyPlayer player : players) {
            Player bukkitPlayer = Bukkit.getPlayer(UUID.fromString(player.getId()));
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.sendMessage(message);
            }
        }
    }

    public static class War {
        private String id;
        private String attackerNationId;
        private String defenderNationId;
        private int attackerKills;
        private int defenderKills;
        private int requiredKills;

        public War(String id, String attackerNationId, String defenderNationId, int requiredKills) {
            this.id = id;
            this.attackerNationId = attackerNationId;
            this.defenderNationId = defenderNationId;
            this.attackerKills = 0;
            this.defenderKills = 0;
            this.requiredKills = requiredKills;
        }

        public String getId() {
            return id;
        }

        public String getAttackerNationId() {
            return attackerNationId;
        }

        public String getDefenderNationId() {
            return defenderNationId;
        }

        public int getAttackerKills() {
            return attackerKills;
        }

        public void setAttackerKills(int attackerKills) {
            this.attackerKills = attackerKills;
        }

        public void incrementAttackerKills() {
            this.attackerKills++;
        }

        public int getDefenderKills() {
            return defenderKills;
        }

        public void setDefenderKills(int defenderKills) {
            this.defenderKills = defenderKills;
        }

        public void incrementDefenderKills() {
            this.defenderKills++;
        }

        public int getRequiredKills() {
            return requiredKills;
        }
    }
}

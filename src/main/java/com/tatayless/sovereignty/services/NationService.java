package com.tatayless.sovereignty.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.ChunkLocation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class NationService {
    private final Sovereignty plugin;
    private final PlayerService playerService;
    private final Map<String, Nation> nations = new HashMap<>();
    private final Map<ChunkLocation, String> chunkOwners = new HashMap<>();
    private final Gson gson = new Gson();

    public NationService(Sovereignty plugin, PlayerService playerService) {
        this.plugin = plugin;
        this.playerService = playerService;
    }

    public void loadNations() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                Result<Record> results = context.select().from("nations").fetch();

                for (Record record : results) {
                    String id = record.get("id", String.class);
                    String name = record.get("name", String.class);
                    double power = record.get("power", Double.class);
                    @SuppressWarnings("unused")
                    int powerLevel = record.get("power_level", Integer.class);

                    Nation nation = new Nation(id, name);
                    nation.setPower(power);

                    // Load claimed chunks
                    String claimedChunksJson = record.get("claimed_chunks", String.class);
                    if (claimedChunksJson != null && !claimedChunksJson.isEmpty()) {
                        List<String> chunkStrings = gson.fromJson(claimedChunksJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String chunkString : chunkStrings) {
                            ChunkLocation chunkLocation = ChunkLocation.fromString(chunkString);
                            nation.addClaimedChunk(chunkLocation);
                            chunkOwners.put(chunkLocation, id);
                        }
                    }

                    // Load annexed chunks
                    String annexedChunksJson = record.get("annexed_chunks", String.class);
                    if (annexedChunksJson != null && !annexedChunksJson.isEmpty()) {
                        List<String> chunkStrings = gson.fromJson(annexedChunksJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String chunkString : chunkStrings) {
                            ChunkLocation chunkLocation = ChunkLocation.fromString(chunkString);
                            nation.addAnnexedChunk(chunkLocation);
                            chunkOwners.put(chunkLocation, id);
                        }
                    }

                    // Load alliances
                    String alliancesJson = record.get("alliances", String.class);
                    if (alliancesJson != null && !alliancesJson.isEmpty()) {
                        List<String> allianceIds = gson.fromJson(alliancesJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String allianceId : allianceIds) {
                            nation.addAlliance(allianceId);
                        }
                    }

                    // Load wars
                    String warsJson = record.get("wars", String.class);
                    if (warsJson != null && !warsJson.isEmpty()) {
                        List<String> warIds = gson.fromJson(warsJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String warId : warIds) {
                            nation.addWar(warId);
                        }
                    }

                    // Set president
                    String presidentId = record.get("president_id", String.class);
                    nation.setPresidentId(presidentId);

                    // Load senators
                    String senatorsJson = record.get("senators", String.class);
                    if (senatorsJson != null && !senatorsJson.isEmpty()) {
                        List<String> senatorIds = gson.fromJson(senatorsJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String senatorId : senatorIds) {
                            nation.addSenator(senatorId);
                        }
                    }

                    // Load soldiers
                    String soldiersJson = record.get("soldiers", String.class);
                    if (soldiersJson != null && !soldiersJson.isEmpty()) {
                        List<String> soldierIds = gson.fromJson(soldiersJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String soldierId : soldierIds) {
                            nation.addSoldier(soldierId);
                        }
                    }

                    // Load citizens
                    String citizensJson = record.get("citizens", String.class);
                    if (citizensJson != null && !citizensJson.isEmpty()) {
                        List<String> citizenIds = gson.fromJson(citizensJson, new TypeToken<List<String>>() {
                        }.getType());
                        for (String citizenId : citizenIds) {
                            nation.addCitizen(citizenId);
                        }
                    }

                    nations.put(id, nation);
                }

                plugin.getLogger().info("Loaded " + nations.size() + " nations from database");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load nations: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Nation> createNation(String name, Player founder) {
        SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(founder.getUniqueId().toString());
        if (sovereigntyPlayer == null) {
            sovereigntyPlayer = playerService.createPlayer(founder);
        }

        if (sovereigntyPlayer.hasNation()) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if nation name already exists
        if (getNationByName(name) != null) {
            return CompletableFuture.completedFuture(null);
        }

        final String nationId = UUID.randomUUID().toString();
        final Nation nation = new Nation(nationId, name);

        // Create a final reference to sovereigntyPlayer for use in the lambda
        final SovereigntyPlayer finalSovereigntyPlayer = sovereigntyPlayer;

        nation.setPresidentId(finalSovereigntyPlayer.getId());

        // Update player
        finalSovereigntyPlayer.setNationId(nationId);
        finalSovereigntyPlayer.setRole(Nation.Role.PRESIDENT);

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Nation>() {
                @Override
                public Nation execute(Connection connection, DSLContext context) throws SQLException {
                    context.insertInto(
                            DSL.table("nations"))
                            .set(DSL.field("id"), nationId)
                            .set(DSL.field("name"), name)
                            .set(DSL.field("power"), 1.0)
                            .set(DSL.field("power_level"), 1)
                            .set(DSL.field("president_id"), finalSovereigntyPlayer.getId())
                            .execute();

                    // Update player
                    playerService.updatePlayer(finalSovereigntyPlayer);

                    nations.put(nationId, nation);
                    return nation;
                }
            });
        });
    }

    public CompletableFuture<Boolean> disbandNation(String nationId, String playerId) {
        Nation nation = getNation(nationId);
        if (nation == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Only president can disband
        if (!nation.getPresidentId().equals(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Remove all players from nation
                List<SovereigntyPlayer> nationPlayers = playerService.getPlayersByNation(nationId);
                for (SovereigntyPlayer player : nationPlayers) {
                    player.setNationId(null);
                    player.setRole(null);
                    playerService.updatePlayer(player);
                }

                // Delete nation
                context.deleteFrom(DSL.table("nations"))
                        .where(DSL.field("id").eq(nationId))
                        .execute();

                // Remove from chunk map
                for (ChunkLocation chunk : nation.getClaimedChunks()) {
                    chunkOwners.remove(chunk);
                }

                for (ChunkLocation chunk : nation.getAnnexedChunks()) {
                    chunkOwners.remove(chunk);
                }

                nations.remove(nationId);
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to disband nation: " + e.getMessage());
                return false;
            }
        });
    }

    public Nation getNation(String id) {
        return nations.get(id);
    }

    public Nation getNationByName(String name) {
        return nations.values().stream()
                .filter(nation -> nation.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public Nation getNationByChunk(ChunkLocation chunkLocation) {
        String nationId = chunkOwners.get(chunkLocation);
        if (nationId == null) {
            return null;
        }
        return nations.get(nationId);
    }

    public Nation getPlayerNation(String playerId) {
        SovereigntyPlayer player = playerService.getPlayer(playerId);
        if (player == null || player.getNationId() == null) {
            return null;
        }
        return nations.get(player.getNationId());
    }

    public List<Nation> getAllNations() {
        return new ArrayList<>(nations.values());
    }

    public boolean isChunkClaimed(ChunkLocation chunkLocation) {
        return chunkOwners.containsKey(chunkLocation);
    }

    public CompletableFuture<Boolean> claimChunk(String nationId, ChunkLocation chunkLocation) {
        Nation nation = getNation(nationId);
        if (nation == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if chunk is already claimed
        if (isChunkClaimed(chunkLocation)) {
            return CompletableFuture.completedFuture(false);
        }

        // Check power level limits
        int maxChunks = plugin.getConfigManager().getMaxChunksForPowerLevel(nation.getPowerLevel());
        if (nation.getClaimedChunks().size() >= maxChunks) {
            return CompletableFuture.completedFuture(false);
        }

        nation.addClaimedChunk(chunkLocation);
        chunkOwners.put(chunkLocation, nationId);

        return saveNation(nation);
    }

    public CompletableFuture<Boolean> unclaimChunk(String nationId, ChunkLocation chunkLocation) {
        Nation nation = getNation(nationId);
        if (nation == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if chunk belongs to nation
        if (!nationId.equals(chunkOwners.get(chunkLocation))) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if it's a claimed or annexed chunk
        boolean isAnnexed = nation.getAnnexedChunks().contains(chunkLocation);

        if (isAnnexed) {
            nation.removeAnnexedChunk(chunkLocation);
        } else {
            nation.removeClaimedChunk(chunkLocation);
        }

        chunkOwners.remove(chunkLocation);

        return saveNation(nation);
    }

    public CompletableFuture<Boolean> annexChunk(String nationId, ChunkLocation chunkLocation) {
        Nation nation = getNation(nationId);
        if (nation == null) {
            return CompletableFuture.completedFuture(false);
        }

        nation.addAnnexedChunk(chunkLocation);
        chunkOwners.put(chunkLocation, nationId);

        return saveNation(nation);
    }

    public CompletableFuture<Boolean> appointSenator(String nationId, String playerId, String targetId) {
        Nation nation = getNation(nationId);
        if (nation == null || !nation.getPresidentId().equals(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        SovereigntyPlayer targetPlayer = playerService.getPlayer(targetId);
        if (targetPlayer == null || !targetPlayer.getNationId().equals(nationId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Update nation
        nation.addSenator(targetId);

        // Update player role
        targetPlayer.setRole(Nation.Role.SENATOR);
        playerService.updatePlayer(targetPlayer);

        return saveNation(nation);
    }

    public CompletableFuture<Boolean> appointSoldier(String nationId, String playerId, String targetId) {
        Nation nation = getNation(nationId);
        if (nation == null || (!nation.getPresidentId().equals(playerId) && !nation.getSenators().contains(playerId))) {
            return CompletableFuture.completedFuture(false);
        }

        SovereigntyPlayer targetPlayer = playerService.getPlayer(targetId);
        if (targetPlayer == null || !targetPlayer.getNationId().equals(nationId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Update nation
        nation.addSoldier(targetId);

        // Update player role
        targetPlayer.setRole(Nation.Role.SOLDIER);
        targetPlayer.setSoldierLives(plugin.getConfigManager().getSoldierLivesForPowerLevel(nation.getPowerLevel()));
        playerService.updatePlayer(targetPlayer);

        return saveNation(nation);
    }

    public CompletableFuture<Boolean> saveNation(Nation nation) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection connection, DSLContext context) throws SQLException {
                    // Convert sets to JSON
                    String claimedChunksJson = gson.toJson(
                            nation.getClaimedChunks().stream()
                                    .map(ChunkLocation::toString)
                                    .collect(Collectors.toList()));

                    String annexedChunksJson = gson.toJson(
                            nation.getAnnexedChunks().stream()
                                    .map(ChunkLocation::toString)
                                    .collect(Collectors.toList()));

                    String alliancesJson = gson.toJson(new ArrayList<>(nation.getAlliances()));
                    String warsJson = gson.toJson(new ArrayList<>(nation.getWars()));
                    String senatorsJson = gson.toJson(new ArrayList<>(nation.getSenators()));
                    String soldiersJson = gson.toJson(new ArrayList<>(nation.getSoldiers()));
                    String citizensJson = gson.toJson(new ArrayList<>(nation.getCitizens()));

                    context.update(DSL.table("nations"))
                            .set(DSL.field("name"), nation.getName())
                            .set(DSL.field("power"), nation.getPower())
                            .set(DSL.field("power_level"), nation.getPowerLevel())
                            .set(DSL.field("claimed_chunks"), claimedChunksJson)
                            .set(DSL.field("annexed_chunks"), annexedChunksJson)
                            .set(DSL.field("alliances"), alliancesJson)
                            .set(DSL.field("wars"), warsJson)
                            .set(DSL.field("president_id"), nation.getPresidentId())
                            .set(DSL.field("senators"), senatorsJson)
                            .set(DSL.field("soldiers"), soldiersJson)
                            .set(DSL.field("citizens"), citizensJson)
                            .where(DSL.field("id").eq(nation.getId()))
                            .execute();

                    return true;
                }
            });
        });
    }
}

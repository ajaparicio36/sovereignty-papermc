package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
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
import java.util.concurrent.ConcurrentHashMap;

public class PlayerService {
    private final Sovereignty plugin;
    private final Map<String, SovereigntyPlayer> playerCache = new ConcurrentHashMap<>();

    public PlayerService(Sovereignty plugin) {
        this.plugin = plugin;
    }

    public void loadPlayers() {
        CompletableFuture.runAsync(() -> {
            DSLContext context = null;
            Connection conn = null;
            try {
                conn = plugin.getDatabaseManager().getConnection();
                context = plugin.getDatabaseManager().createContext();
                Result<Record> results = context.select().from("players").fetch();

                for (Record record : results) {
                    String id = record.get("id", String.class);
                    String name = record.get("name", String.class);
                    String nationId = record.get("nation_id", String.class);
                    String roleStr = record.get("role", String.class);
                    int soldierLives = record.get("soldier_lives", Integer.class);

                    SovereigntyPlayer player = new SovereigntyPlayer(id, name);
                    player.setNationId(nationId);
                    if (roleStr != null && !roleStr.isEmpty()) {
                        player.setRole(roleFromString(roleStr));
                    }
                    player.setSoldierLives(soldierLives);

                    playerCache.put(id, player);
                }

                plugin.getLogger().info("Loaded " + playerCache.size() + " players from database");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load players: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Failed to close connection: " + e.getMessage());
                    }
                }
            }
        });
    }

    public SovereigntyPlayer getPlayer(String id) {
        return playerCache.get(id);
    }

    public SovereigntyPlayer getPlayerByName(String name) {
        return playerCache.values().stream()
                .filter(player -> player.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public SovereigntyPlayer createPlayer(Player player) {
        String id = player.getUniqueId().toString();
        String name = player.getName();

        SovereigntyPlayer sovereigntyPlayer = new SovereigntyPlayer(id, name);
        playerCache.put(id, sovereigntyPlayer);

        CompletableFuture.runAsync(() -> {
            Connection conn = null;
            try {
                conn = plugin.getDatabaseManager().getConnection();
                DSLContext context = plugin.getDatabaseManager().createContext();
                context.insertInto(
                        DSL.table("players"),
                        DSL.field("id"),
                        DSL.field("name")).values(
                                id,
                                name)
                        .execute();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create player: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Failed to close connection: " + e.getMessage());
                    }
                }
            }
        });

        return sovereigntyPlayer;
    }

    public List<SovereigntyPlayer> getPlayersByNation(String nationId) {
        List<SovereigntyPlayer> players = new ArrayList<>();

        for (SovereigntyPlayer player : playerCache.values()) {
            if (nationId.equals(player.getNationId())) {
                players.add(player);
            }
        }

        return players;
    }

    public CompletableFuture<Boolean> updatePlayer(SovereigntyPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            DSLContext context = null;
            Connection conn = null;
            try {
                conn = plugin.getDatabaseManager().getConnection();
                context = plugin.getDatabaseManager().createContext();
                String roleStr = null;
                if (player.getRole() != null) {
                    roleStr = stringFromRole(player.getRole());
                }

                context.update(DSL.table("players"))
                        .set(DSL.field("name"), player.getName())
                        .set(DSL.field("nation_id"), player.getNationId())
                        .set(DSL.field("role"), roleStr)
                        .set(DSL.field("soldier_lives"), player.getSoldierLives())
                        .where(DSL.field("id").eq(player.getId()))
                        .execute();

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update player: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        plugin.getLogger().severe("Failed to close connection: " + e.getMessage());
                    }
                }
            }
        });
    }

    private String stringFromRole(com.tatayless.sovereignty.models.Nation.Role role) {
        if (role == null)
            return null;

        switch (role) {
            case PRESIDENT:
                return "president";
            case SENATOR:
                return "senator";
            case SOLDIER:
                return "soldier";
            case CITIZEN:
                return "citizen";
            default:
                return null;
        }
    }

    private com.tatayless.sovereignty.models.Nation.Role roleFromString(String roleStr) {
        if (roleStr == null || roleStr.isEmpty())
            return null;

        switch (roleStr.toLowerCase()) {
            case "president":
                return com.tatayless.sovereignty.models.Nation.Role.PRESIDENT;
            case "senator":
                return com.tatayless.sovereignty.models.Nation.Role.SENATOR;
            case "soldier":
                return com.tatayless.sovereignty.models.Nation.Role.SOLDIER;
            case "citizen":
                return com.tatayless.sovereignty.models.Nation.Role.CITIZEN;
            default:
                return null;
        }
    }
}

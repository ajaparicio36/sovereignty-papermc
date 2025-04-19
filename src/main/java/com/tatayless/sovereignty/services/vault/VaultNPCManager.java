package com.tatayless.sovereignty.services.vault;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VaultNPCManager {
    private final Sovereignty plugin;
    private final Map<Integer, String> entityToVault = new HashMap<>(); // Maps entity ID to vault ID

    public VaultNPCManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    public Map<Integer, String> getEntityToVaultMap() {
        return entityToVault;
    }

    public String getVaultIdFromEntity(int entityId) {
        return entityToVault.get(entityId);
    }

    /**
     * Loads all vault NPCs from the database and respawns them in the world
     */
    public void loadAndRespawnNPCs() {
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                // First load the NPC to vault mappings
                Result<Record> npcResults = context.select().from("vault_npcs").fetch();

                for (Record record : npcResults) {
                    int entityId = record.get("entity_id", Integer.class);
                    String vaultId = record.get("nation_vault_id", String.class);
                    entityToVault.put(entityId, vaultId);
                }

                plugin.getLogger().info("Loaded " + entityToVault.size() + " vault NPC mappings from database");

                // Now respawn all NPCs
                respawnAllNPCs(context);

                return null;
            }
        });
    }

    /**
     * Respawns all vault NPCs in their correct locations
     */
    private void respawnAllNPCs(DSLContext context) {
        Result<Record> npcRecords = context.select().from("vault_npcs").fetch();

        if (!npcRecords.isEmpty()) {
            plugin.getLogger().info("Respawning " + npcRecords.size() + " vault NPCs");

            for (Record record : npcRecords) {
                String worldName = record.get("world", String.class);
                double x = record.get("x", Double.class);
                double y = record.get("y", Double.class);
                double z = record.get("z", Double.class);
                @SuppressWarnings("unused")
                String nationId = record.get("nation_id", String.class);
                String vaultId = record.get("nation_vault_id", String.class);

                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location location = new Location(world, x, y, z);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            // Create a new villager
                            Villager villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);

                            // Configure the villager
                            configureVaultNPC(villager);

                            int entityId = villager.getEntityId();
                            entityToVault.put(entityId, vaultId);

                            // Update entity ID in database
                            plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
                                @Override
                                public Void execute(Connection conn, DSLContext ctx) throws SQLException {
                                    ctx.update(DSL.table("vault_npcs"))
                                            .set(DSL.field("entity_id"), entityId)
                                            .where(DSL.field("nation_vault_id").eq(vaultId))
                                            .execute();
                                    return null;
                                }
                            });
                        } catch (Exception e) {
                            plugin.getLogger().severe("Failed to respawn vault NPC: " + e.getMessage());
                        }
                    });
                } else {
                    plugin.getLogger().warning("Could not respawn NPC: world '" + worldName + "' not found");
                }
            }
        }
    }

    /**
     * Applies standard configuration to a vault NPC villager
     */
    private void configureVaultNPC(Villager villager) {
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.customName(net.kyori.adventure.text.Component.text("Nation Vault")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        // Set villager to not despawn
        villager.setPersistent(true);
    }

    /**
     * Creates or moves a vault NPC for a nation
     */
    public CompletableFuture<Boolean> createOrMoveVaultNPC(String nationId, String vaultId, Location location,
            String playerId) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Check if an NPC already exists
                    Record npcRecord = context.select().from("vault_npcs")
                            .where(DSL.field("nation_vault_id").eq(vaultId))
                            .fetchOne();

                    final boolean[] isNewNpc = { npcRecord == null };

                    // Create the entity in the game world
                    CompletableFuture<Integer> entityIdFuture = new CompletableFuture<>();

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            // If we have an existing NPC, remove it first
                            if (!isNewNpc[0]) {
                                int oldEntityId = npcRecord.get("entity_id", Integer.class);
                                entityToVault.remove(oldEntityId);

                                for (org.bukkit.World world : Bukkit.getWorlds()) {
                                    for (Entity entity : world.getEntities()) {
                                        if (entity.getEntityId() == oldEntityId && entity instanceof Villager) {
                                            entity.remove();
                                            break;
                                        }
                                    }
                                }
                            }

                            // Create a new villager
                            Villager villager = (Villager) location.getWorld().spawnEntity(location,
                                    EntityType.VILLAGER);
                            configureVaultNPC(villager);

                            int entityId = villager.getEntityId();
                            entityToVault.put(entityId, vaultId);
                            entityIdFuture.complete(entityId);
                        } catch (Exception e) {
                            plugin.getLogger().severe("Failed to create vault NPC: " + e.getMessage());
                            entityIdFuture.completeExceptionally(e);
                        }
                    });

                    try {
                        // Wait for the entity to be created and get its ID
                        int entityId = entityIdFuture.get(10, TimeUnit.SECONDS);

                        if (isNewNpc[0]) {
                            // Generate a new UUID for the NPC
                            String npcId = UUID.randomUUID().toString();

                            context.insertInto(
                                    DSL.table("vault_npcs"),
                                    DSL.field("id"),
                                    DSL.field("nation_id"),
                                    DSL.field("nation_vault_id"),
                                    DSL.field("coordinates"),
                                    DSL.field("entity_id"),
                                    DSL.field("created_by"),
                                    DSL.field("world"),
                                    DSL.field("x"),
                                    DSL.field("y"),
                                    DSL.field("z")).values(
                                            npcId,
                                            nationId,
                                            vaultId,
                                            location.getX() + "," + location.getY() + "," +
                                                    location.getZ() + "," + location.getWorld().getName(),
                                            entityId,
                                            playerId,
                                            location.getWorld().getName(),
                                            location.getX(),
                                            location.getY(),
                                            location.getZ())
                                    .execute();
                        } else {
                            context.update(DSL.table("vault_npcs"))
                                    .set(DSL.field("entity_id"), entityId)
                                    .set(DSL.field("coordinates"), location.getX() + "," + location.getY() + "," +
                                            location.getZ() + "," + location.getWorld().getName())
                                    .set(DSL.field("world"), location.getWorld().getName())
                                    .set(DSL.field("x"), location.getX())
                                    .set(DSL.field("y"), location.getY())
                                    .set(DSL.field("z"), location.getZ())
                                    .where(DSL.field("nation_vault_id").eq(vaultId))
                                    .execute();
                        }

                        return true;
                    } catch (Exception e) {
                        plugin.getLogger().severe("Failed to create/update vault NPC record: " + e.getMessage());
                        return false;
                    }
                }
            });
        });
    }

    /**
     * Removes a vault NPC for a nation
     */
    public CompletableFuture<Boolean> removeVaultNPC(String nationId, String vaultId) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Find entity ID associated with this vault
                    Integer entityId = null;

                    for (Map.Entry<Integer, String> entry : entityToVault.entrySet()) {
                        if (entry.getValue().equals(vaultId)) {
                            entityId = entry.getKey();
                            break;
                        }
                    }

                    // Delete from database
                    context.deleteFrom(DSL.table("vault_npcs"))
                            .where(DSL.field("nation_vault_id").eq(vaultId))
                            .execute();

                    // Remove from memory and world
                    if (entityId != null) {
                        final int finalEntityId = entityId;
                        entityToVault.remove(entityId);

                        // Find and remove the entity from the world
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (World world : Bukkit.getWorlds()) {
                                for (Entity entity : world.getEntities()) {
                                    if (entity.getEntityId() == finalEntityId && entity instanceof Villager) {
                                        entity.remove();
                                        break;
                                    }
                                }
                            }
                        });
                    }

                    return true;
                }
            });
        });
    }
}

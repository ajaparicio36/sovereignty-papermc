package com.tatayless.sovereignty.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class VaultService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, NationVault> nationVaults = new HashMap<>();
    private final Map<Integer, String> entityToVault = new HashMap<>(); // Maps entity ID to vault ID
    private final Gson gson = new Gson();

    public VaultService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
    }

    public void loadVaults() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Load nation vaults
                Result<Record> results = context.select().from("nation_vaults").fetch();

                for (Record record : results) {
                    String id = record.get("id", String.class);
                    String nationId = record.get("nation_id", String.class);
                    String itemsJson = record.get("items", String.class);
                    String overflowItemsJson = record.get("overflow_items", String.class);
                    Object overflowExpiryObj = record.get("overflow_expiry");

                    ItemStack[] items = null;
                    ItemStack[] overflowItems = null;
                    Date overflowExpiry = null;

                    if (itemsJson != null && !itemsJson.isEmpty()) {
                        List<Map<String, Object>> itemsList = gson.fromJson(itemsJson,
                                new TypeToken<List<Map<String, Object>>>() {
                                }.getType());
                        items = deserializeItems(itemsList);
                    }

                    if (overflowItemsJson != null && !overflowItemsJson.isEmpty()) {
                        List<Map<String, Object>> itemsList = gson.fromJson(overflowItemsJson,
                                new TypeToken<List<Map<String, Object>>>() {
                                }.getType());
                        overflowItems = deserializeItems(itemsList);
                    }

                    if (overflowExpiryObj != null) {
                        if (overflowExpiryObj instanceof Timestamp) {
                            overflowExpiry = new Date(((Timestamp) overflowExpiryObj).getTime());
                        } else if (overflowExpiryObj instanceof String) {
                            // SQLite dates are stored as strings
                            LocalDateTime ldt = LocalDateTime.parse((String) overflowExpiryObj);
                            overflowExpiry = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                        }
                    }

                    NationVault vault = new NationVault(id, nationId, items, overflowItems, overflowExpiry);
                    nationVaults.put(nationId, vault);
                }

                // Load vault NPCs
                Result<Record> npcResults = context.select().from("vault_npcs").fetch();

                for (Record record : npcResults) {
                    int entityId = record.get("entity_id", Integer.class);
                    String vaultId = record.get("nation_vault_id", String.class);
                    entityToVault.put(entityId, vaultId);
                }

                plugin.getLogger().info("Loaded " + nationVaults.size() + " nation vaults from database");

                // Start a task to clean up expired overflow items
                scheduleOverflowCleanup();

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load vaults: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void scheduleOverflowCleanup() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Date now = new Date();
                List<NationVault> vaultsToUpdate = new ArrayList<>();

                for (NationVault vault : nationVaults.values()) {
                    if (vault.getOverflowExpiry() != null && vault.getOverflowExpiry().before(now)) {
                        vault.clearOverflow();
                        vaultsToUpdate.add(vault);
                    }
                }

                for (NationVault vault : vaultsToUpdate) {
                    saveVault(vault);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200, 1200); // Run every minute (20 ticks/sec * 60 sec)
    }

    public void openVault(Player player, String nationId) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("vault.no-vault"));
            return;
        }

        getOrCreateVault(nationId).thenAccept(vault -> {
            if (vault != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int rows = plugin.getConfigManager().getNationVaultRows();
                    int size = rows * 9;

                    Inventory inventory = Bukkit.createInventory(null, size,
                            net.kyori.adventure.text.Component.text("Nation Vault: " + nation.getName()));

                    if (vault.getItems() != null) {
                        inventory.setContents(vault.getItems());
                    }

                    player.openInventory(inventory);
                });
            } else {
                player.sendMessage(plugin.getLocalizationManager().getMessage("vault.no-vault"));
            }
        });
    }

    public CompletableFuture<NationVault> getOrCreateVault(String nationId) {
        // Check if vault exists in memory
        NationVault vault = nationVaults.get(nationId);
        if (vault != null) {
            return CompletableFuture.completedFuture(vault);
        }

        // Create new vault
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Check if vault exists in DB
                Record record = context.select().from("nation_vaults")
                        .where(DSL.field("nation_id").eq(nationId))
                        .fetchOne();

                if (record != null) {
                    String id = record.get("id", String.class);
                    String itemsJson = record.get("items", String.class);
                    String overflowItemsJson = record.get("overflow_items", String.class);
                    Object overflowExpiryObj = record.get("overflow_expiry");

                    ItemStack[] items = null;
                    ItemStack[] overflowItems = null;
                    Date overflowExpiry = null;

                    if (itemsJson != null && !itemsJson.isEmpty()) {
                        List<Map<String, Object>> itemsList = gson.fromJson(itemsJson,
                                new TypeToken<List<Map<String, Object>>>() {
                                }.getType());
                        items = deserializeItems(itemsList);
                    }

                    if (overflowItemsJson != null && !overflowItemsJson.isEmpty()) {
                        List<Map<String, Object>> itemsList = gson.fromJson(overflowItemsJson,
                                new TypeToken<List<Map<String, Object>>>() {
                                }.getType());
                        overflowItems = deserializeItems(itemsList);
                    }

                    if (overflowExpiryObj != null) {
                        if (overflowExpiryObj instanceof Timestamp) {
                            overflowExpiry = new Date(((Timestamp) overflowExpiryObj).getTime());
                        } else if (overflowExpiryObj instanceof String) {
                            // SQLite dates are stored as strings
                            LocalDateTime ldt = LocalDateTime.parse((String) overflowExpiryObj);
                            overflowExpiry = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                        }
                    }

                    NationVault existingVault = new NationVault(id, nationId, items, overflowItems, overflowExpiry);
                    nationVaults.put(nationId, existingVault);
                    return existingVault;
                }

                // Create new vault in DB
                String vaultId = UUID.randomUUID().toString();
                context.insertInto(
                        DSL.table("nation_vaults"),
                        DSL.field("id"),
                        DSL.field("nation_id")).values(
                                vaultId,
                                nationId)
                        .execute();

                NationVault newVault = new NationVault(vaultId, nationId, null, null, null);
                nationVaults.put(nationId, newVault);
                return newVault;

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create vault: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }

    public void updateVaultContents(String nationId, ItemStack[] contents) {
        NationVault vault = nationVaults.get(nationId);
        if (vault == null) {
            return;
        }

        vault.setItems(contents);
        saveVault(vault);
    }

    public CompletableFuture<Boolean> saveVault(NationVault vault) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                DSLContext context = plugin.getDatabaseManager().createContextSafe(conn);
                // Serialize items
                String itemsJson = null;
                String overflowItemsJson = null;

                if (vault.getItems() != null) {
                    itemsJson = gson.toJson(serializeItems(vault.getItems()));
                }

                if (vault.getOverflowItems() != null) {
                    overflowItemsJson = gson.toJson(serializeItems(vault.getOverflowItems()));
                }

                // Update vault
                context.update(DSL.table("nation_vaults"))
                        .set(DSL.field("items"), itemsJson)
                        .set(DSL.field("overflow_items"), overflowItemsJson)
                        .set(DSL.field("overflow_expiry"),
                                vault.getOverflowExpiry() != null ? new Timestamp(vault.getOverflowExpiry().getTime())
                                        : null)
                        .where(DSL.field("id").eq(vault.getId()))
                        .execute();

                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save vault: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public CompletableFuture<Boolean> createVaultNPC(String nationId, Location location, String playerId) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null || !nation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        return getOrCreateVault(nationId).thenCompose(vault -> {
            if (vault == null) {
                return CompletableFuture.completedFuture(false);
            }

            return CompletableFuture.supplyAsync(() -> {
                DSLContext context = null;
                Connection conn = null;
                try {
                    // Create the NPC entity
                    final Villager[] npc = { null };
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        npc[0] = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
                        npc[0].customName(net.kyori.adventure.text.Component.text("Vault: " + nation.getName()));
                        npc[0].setCustomNameVisible(true);
                        npc[0].setProfession(Villager.Profession.LIBRARIAN);
                        npc[0].setAI(false);
                        npc[0].setInvulnerable(true);
                        npc[0].setSilent(true);
                    });

                    // Wait for entity to be created
                    Thread.sleep(100);

                    // Store the NPC in database
                    conn = plugin.getDatabaseManager().getConnection();
                    context = plugin.getDatabaseManager().createContext();
                    int entityId = npc[0].getEntityId();
                    String npcId = UUID.randomUUID().toString();
                    String locationStr = String.format("%f,%f,%f,%s",
                            location.getX(), location.getY(), location.getZ(), location.getWorld().getName());

                    context.insertInto(
                            DSL.table("vault_npcs"),
                            DSL.field("id"),
                            DSL.field("nation_id"),
                            DSL.field("nation_vault_id"),
                            DSL.field("coordinates"),
                            DSL.field("entity_id")).values(
                                    npcId,
                                    nationId,
                                    vault.getId(),
                                    locationStr,
                                    entityId)
                            .execute();

                    entityToVault.put(entityId, vault.getId());
                    return true;
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to create vault NPC: " + e.getMessage());
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
        });
    }

    public String getVaultIdFromEntity(int entityId) {
        return entityToVault.get(entityId);
    }

    private ItemStack[] deserializeItems(List<Map<String, Object>> itemsList) {
        if (itemsList == null)
            return null;

        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemsList) {
            try {
                // This is a simplified version - in reality, you'd need to handle
                // all ItemStack properties including metadata, enchantments, etc.
                ItemStack item = ItemStack.deserialize(itemMap);
                items.add(item);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
            }
        }

        return items.toArray(new ItemStack[0]);
    }

    private List<Map<String, Object>> serializeItems(ItemStack[] items) {
        if (items == null)
            return null;

        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                try {
                    Map<String, Object> serialized = item.serialize();
                    itemsList.add(serialized);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to serialize item: " + e.getMessage());
                }
            }
        }

        return itemsList;
    }

    public static class NationVault {
        private final String id;
        private final String nationId;
        private ItemStack[] items;
        private ItemStack[] overflowItems;
        private Date overflowExpiry;

        public NationVault(String id, String nationId, ItemStack[] items,
                ItemStack[] overflowItems, Date overflowExpiry) {
            this.id = id;
            this.nationId = nationId;
            this.items = items;
            this.overflowItems = overflowItems;
            this.overflowExpiry = overflowExpiry;
        }

        public String getId() {
            return id;
        }

        public String getNationId() {
            return nationId;
        }

        public ItemStack[] getItems() {
            return items;
        }

        public void setItems(ItemStack[] items) {
            this.items = items;
        }

        public ItemStack[] getOverflowItems() {
            return overflowItems;
        }

        public void setOverflowItems(ItemStack[] overflowItems) {
            this.overflowItems = overflowItems;
        }

        public Date getOverflowExpiry() {
            return overflowExpiry;
        }

        public void setOverflowExpiry(Date overflowExpiry) {
            this.overflowExpiry = overflowExpiry;
        }

        public void clearOverflow() {
            this.overflowItems = null;
            this.overflowExpiry = null;
        }
    }
}

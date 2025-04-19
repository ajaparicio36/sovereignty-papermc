package com.tatayless.sovereignty.services.vault;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.services.VaultService;
import org.bukkit.inventory.ItemStack;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class VaultStorageManager {
    private final Sovereignty plugin;
    private final Gson gson = new Gson();

    public VaultStorageManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves a vault to the database
     */
    public CompletableFuture<Boolean> saveVault(VaultService.NationVault vault) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    Map<String, List<Map<String, Object>>> pagesMap = new HashMap<>();
                    for (Map.Entry<Integer, ItemStack[]> entry : vault.getPages().entrySet()) {
                        if (entry.getValue() != null) {
                            pagesMap.put(String.valueOf(entry.getKey()), serializeItems(entry.getValue()));
                        }
                    }

                    String itemsJson = gson.toJson(pagesMap);
                    String overflowItemsJson = null;

                    if (vault.getOverflowItems() != null) {
                        overflowItemsJson = gson.toJson(serializeItems(vault.getOverflowItems()));
                    }

                    context.update(DSL.table("nation_vaults"))
                            .set(DSL.field("items"), itemsJson)
                            .set(DSL.field("overflow_items"), overflowItemsJson)
                            .set(DSL.field("overflow_expiry"),
                                    vault.getOverflowExpiry() != null
                                            ? new Timestamp(vault.getOverflowExpiry().getTime())
                                            : null)
                            .where(DSL.field("id").eq(vault.getId()))
                            .execute();

                    return true;
                }
            });
        });
    }

    /**
     * Loads or creates a vault for a nation
     */
    public CompletableFuture<VaultService.NationVault> getOrCreateVault(String nationId,
            Map<String, VaultService.NationVault> nationVaults) {
        VaultService.NationVault vault = nationVaults.get(nationId);
        if (vault != null) {
            return CompletableFuture.completedFuture(vault);
        }

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<VaultService.NationVault>() {
                @Override
                public VaultService.NationVault execute(Connection conn, DSLContext context) throws SQLException {
                    Record record = context.select().from("nation_vaults")
                            .where(DSL.field("nation_id").eq(nationId))
                            .fetchOne();

                    if (record != null) {
                        String id = record.get("id", String.class);
                        String itemsJson = record.get("items", String.class);
                        String overflowItemsJson = record.get("overflow_items", String.class);
                        Object overflowExpiryObj = record.get("overflow_expiry");

                        Map<Integer, ItemStack[]> vaultPages = new HashMap<>();
                        ItemStack[] overflowItems = null;
                        Date overflowExpiry = null;

                        if (itemsJson != null && !itemsJson.isEmpty()) {
                            try {
                                Map<String, List<Map<String, Object>>> pagesMap = gson.fromJson(itemsJson,
                                        new TypeToken<Map<String, List<Map<String, Object>>>>() {
                                        }.getType());
                                for (String pageKey : pagesMap.keySet()) {
                                    try {
                                        int pageNum = Integer.parseInt(pageKey);
                                        List<Map<String, Object>> itemsList = pagesMap.get(pageKey);
                                        ItemStack[] items = deserializeItems(itemsList);
                                        vaultPages.put(pageNum, items);
                                    } catch (NumberFormatException e) {
                                        plugin.getLogger().warning("Invalid page number in vault: " + pageKey);
                                    }
                                }
                            } catch (Exception e) {
                                try {
                                    List<Map<String, Object>> itemsList = gson.fromJson(itemsJson,
                                            new TypeToken<List<Map<String, Object>>>() {
                                            }.getType());
                                    ItemStack[] items = deserializeItems(itemsList);
                                    vaultPages.put(0, items);
                                } catch (Exception e2) {
                                    plugin.getLogger().severe("Failed to parse vault items: " + e2.getMessage());
                                }
                            }
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
                                LocalDateTime ldt = LocalDateTime.parse((String) overflowExpiryObj);
                                overflowExpiry = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                            }
                        }

                        VaultService.NationVault existingVault = new VaultService.NationVault(id, nationId, vaultPages,
                                overflowItems, overflowExpiry);
                        nationVaults.put(nationId, existingVault);
                        return existingVault;
                    }

                    String vaultId = UUID.randomUUID().toString();
                    context.insertInto(
                            DSL.table("nation_vaults"),
                            DSL.field("id"),
                            DSL.field("nation_id")).values(
                                    vaultId,
                                    nationId)
                            .execute();

                    VaultService.NationVault newVault = new VaultService.NationVault(vaultId, nationId, new HashMap<>(),
                            null, null);
                    nationVaults.put(nationId, newVault);
                    return newVault;
                }
            });
        });
    }

    /**
     * Deserializes items from database format
     */
    public ItemStack[] deserializeItems(List<Map<String, Object>> itemsList) {
        if (itemsList == null)
            return null;

        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemsList) {
            try {
                ItemStack item = ItemStack.deserialize(itemMap);
                items.add(item);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
            }
        }

        return items.toArray(new ItemStack[0]);
    }

    /**
     * Serializes items for database storage
     */
    public List<Map<String, Object>> serializeItems(ItemStack[] items) {
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

    /**
     * Schedules a task to clean up expired overflow items
     */
    public void scheduleOverflowCleanup(Map<String, VaultService.NationVault> nationVaults) {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Date now = new Date();
            List<VaultService.NationVault> vaultsToUpdate = new ArrayList<>();

            for (VaultService.NationVault vault : nationVaults.values()) {
                if (vault.getOverflowExpiry() != null && vault.getOverflowExpiry().before(now)) {
                    vault.clearOverflow();
                    vaultsToUpdate.add(vault);
                }
            }

            for (VaultService.NationVault vault : vaultsToUpdate) {
                saveVault(vault);
            }
        }, 1200, 1200); // Check every minute (20 ticks * 60)
    }
}

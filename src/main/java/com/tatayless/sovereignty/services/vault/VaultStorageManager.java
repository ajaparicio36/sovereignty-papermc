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
import java.util.logging.Level;

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
        // Capture state immediately for logging, before async execution
        final String vaultId = vault.getId();
        final String nationId = vault.getNationId();
        plugin.getLogger().info("[DEBUG] Received request to save vault " + vaultId + " for nation " + nationId);

        return CompletableFuture.supplyAsync(() -> {
            // Log inside the async task
            plugin.getLogger().info(
                    "[DEBUG] Starting async save task for vault " + vaultId + " (Nation: " + nationId + ")");
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    try {
                        plugin.getLogger().info("[DEBUG] Serializing vault data for vault " + vaultId);
                        Map<String, List<Map<String, Object>>> pagesMap = new HashMap<>();
                        int totalItemsAcrossPages = 0;
                        int pageCount = 0;

                        // Use getter and add null check
                        Map<Integer, ItemStack[]> vaultPages = vault.getPages();
                        if (vaultPages == null) {
                            plugin.getLogger()
                                    .severe("[ERROR] Vault " + vaultId
                                            + " has null pages map during save! This should not happen.");
                            vaultPages = new HashMap<>();
                        }

                        for (Map.Entry<Integer, ItemStack[]> entry : vaultPages.entrySet()) {
                            Integer pageIndex = entry.getKey();
                            ItemStack[] items = entry.getValue();

                            if (items != null) {
                                List<Map<String, Object>> serializedItems = serializeItems(items);
                                int pageItemCount = (int) Arrays.stream(items).filter(Objects::nonNull).count();
                                totalItemsAcrossPages += pageItemCount;
                                pageCount++;

                                if (serializedItems != null && !serializedItems.isEmpty()) {
                                    pagesMap.put(String.valueOf(pageIndex), serializedItems);
                                    plugin.getLogger().info("[DEBUG] Serialized " + pageItemCount +
                                            " items (found in original array) for vault " + vaultId + " page "
                                            + pageIndex +
                                            ". Serialized list size: " + serializedItems.size());
                                } else if (pageItemCount > 0) {
                                    plugin.getLogger()
                                            .warning("[DEBUG] Page " + pageIndex + " for vault " + vaultId +
                                                    " had " + pageItemCount
                                                    + " items but resulted in empty/null serialized list.");
                                }
                            } else {
                                plugin.getLogger().warning("[DEBUG] Page " + pageIndex + " for vault " + vaultId
                                        + " has null ItemStack array.");
                            }
                        }

                        String itemsJson = gson.toJson(pagesMap);
                        String overflowItemsJson = null;
                        int overflowItemCount = 0;

                        if (vault.getOverflowItems() != null) {
                            List<Map<String, Object>> serializedOverflow = serializeItems(vault.getOverflowItems());
                            if (serializedOverflow != null) {
                                overflowItemsJson = gson.toJson(serializedOverflow);
                                overflowItemCount = (int) Arrays.stream(vault.getOverflowItems())
                                        .filter(Objects::nonNull).count();
                                plugin.getLogger().info("[DEBUG] Serialized " + overflowItemCount
                                        + " overflow items for vault " + vaultId);
                            }
                        }

                        plugin.getLogger().info("[DEBUG] Preparing DB operation for vault " + vaultId + ": " +
                                totalItemsAcrossPages + " items across " + pageCount + " pages. " +
                                overflowItemCount + " overflow items. " +
                                "Pages JSON size: " + (itemsJson != null ? itemsJson.length() : 0) + " chars. " +
                                "Overflow JSON size: " + (overflowItemsJson != null ? overflowItemsJson.length() : 0)
                                + " chars.");
                        if (plugin.getLogger().isLoggable(Level.FINE)) {
                            plugin.getLogger().fine("[DEBUG] Vault " + vaultId + " Pages JSON to save: " + itemsJson);
                            plugin.getLogger()
                                    .fine("[DEBUG] Vault " + vaultId + " Overflow JSON to save: " + overflowItemsJson);
                        }

                        Record existingRecord = context.select(DSL.field("id"))
                                .from("nation_vaults")
                                .where(DSL.field("id").eq(vaultId))
                                .fetchOne();

                        boolean success;
                        Timestamp expiryTimestamp = vault.getOverflowExpiry() != null
                                ? new Timestamp(vault.getOverflowExpiry().getTime())
                                : null;

                        if (existingRecord != null) {
                            plugin.getLogger().info("[DEBUG] Updating existing vault record for " + vaultId);
                            int updated = context.update(DSL.table("nation_vaults"))
                                    .set(DSL.field("items"), itemsJson)
                                    .set(DSL.field("overflow_items"), overflowItemsJson)
                                    .set(DSL.field("overflow_expiry"), expiryTimestamp)
                                    .where(DSL.field("id").eq(vaultId))
                                    .execute();

                            success = updated > 0;
                            if (!success)
                                plugin.getLogger()
                                        .warning("[DEBUG] Vault update query affected 0 rows for ID: " + vaultId);

                        } else {
                            plugin.getLogger().info("[DEBUG] Creating new vault record for " + vaultId);
                            int inserted = context.insertInto(DSL.table("nation_vaults"))
                                    .set(DSL.field("id"), vaultId)
                                    .set(DSL.field("nation_id"), nationId)
                                    .set(DSL.field("items"), itemsJson)
                                    .set(DSL.field("overflow_items"), overflowItemsJson)
                                    .set(DSL.field("overflow_expiry"), expiryTimestamp)
                                    .execute();

                            success = inserted > 0;
                            if (!success)
                                plugin.getLogger()
                                        .warning("[DEBUG] Vault insert query affected 0 rows for ID: " + vaultId);
                        }

                        plugin.getLogger().info("[DEBUG] Vault save database operation " +
                                (success ? "successful" : "failed") + " for vault " + vaultId);
                        return success;
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "[ERROR] Error during database operation for vault " + vaultId + ": " + e.getMessage(),
                                e);
                        return false;
                    }
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE,
                    "[ERROR] Uncaught exception in async vault save task for vault " + vaultId, ex);
            return false;
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
                        try {
                            String id = record.get("id", String.class);
                            String itemsJson = record.get("items", String.class);
                            String overflowItemsJson = record.get("overflow_items", String.class);
                            Object overflowExpiryObj = record.get("overflow_expiry");

                            Map<Integer, ItemStack[]> vaultPages = new HashMap<>();
                            ItemStack[] overflowItems = null;
                            Date overflowExpiry = null;

                            // Load the items from the database
                            if (itemsJson != null && !itemsJson.isEmpty()) {
                                try {
                                    Map<String, List<Map<String, Object>>> pagesMap = gson.fromJson(itemsJson,
                                            new TypeToken<Map<String, List<Map<String, Object>>>>() {
                                            }.getType());

                                    if (pagesMap != null) {
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
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to parse pages map: " + e.getMessage());

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
                                try {
                                    List<Map<String, Object>> itemsList = gson.fromJson(overflowItemsJson,
                                            new TypeToken<List<Map<String, Object>>>() {
                                            }.getType());
                                    overflowItems = deserializeItems(itemsList);
                                } catch (Exception e) {
                                    plugin.getLogger().warning("Failed to parse overflow items: " + e.getMessage());
                                }
                            }

                            if (overflowExpiryObj != null) {
                                if (overflowExpiryObj instanceof Timestamp) {
                                    overflowExpiry = new Date(((Timestamp) overflowExpiryObj).getTime());
                                } else if (overflowExpiryObj instanceof String) {
                                    try {
                                        LocalDateTime ldt = LocalDateTime.parse((String) overflowExpiryObj);
                                        overflowExpiry = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                                    } catch (Exception e) {
                                        plugin.getLogger()
                                                .warning("Failed to parse overflow expiry: " + e.getMessage());
                                    }
                                }
                            }

                            VaultService.NationVault existingVault = new VaultService.NationVault(id, nationId,
                                    vaultPages,
                                    overflowItems, overflowExpiry);
                            // Mark as clean since we just loaded from DB
                            existingVault.markClean();
                            nationVaults.put(nationId, existingVault);
                            return existingVault;
                        } catch (Exception e) {
                            plugin.getLogger().severe("Error loading vault: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // Create a new vault if we couldn't load an existing one
                    String vaultId = UUID.randomUUID().toString();
                    try {
                        context.insertInto(
                                DSL.table("nation_vaults"),
                                DSL.field("id"),
                                DSL.field("nation_id"))
                                .values(vaultId, nationId)
                                .execute();

                        VaultService.NationVault newVault = new VaultService.NationVault(vaultId, nationId,
                                new HashMap<>(),
                                null, null);
                        // Mark as clean since we just created
                        newVault.markClean();
                        nationVaults.put(nationId, newVault);
                        return newVault;
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error creating new vault: " + e.getMessage());
                        e.printStackTrace();
                    }

                    return null;
                }
            });
        });
    }

    /**
     * Deserializes items from database format
     */
    public ItemStack[] deserializeItems(List<Map<String, Object>> itemsList) {
        if (itemsList == null || itemsList.isEmpty())
            return new ItemStack[0];

        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemsList) {
            try {
                Map<String, Object> fixedMap = new HashMap<>();
                for (Map.Entry<String, Object> entry : itemMap.entrySet()) {
                    if (entry.getValue() instanceof Number && entry.getKey().equals("amount")) {
                        fixedMap.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                    } else {
                        fixedMap.put(entry.getKey(), entry.getValue());
                    }
                }

                ItemStack item = ItemStack.deserialize(fixedMap);
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
        if (items == null) {
            plugin.getLogger().info("[DEBUG] Attempted to serialize null ItemStack array.");
            return new ArrayList<>();
        }

        List<Map<String, Object>> itemsList = new ArrayList<>();
        int nullItems = 0;
        int successItems = 0;
        int errorItems = 0;

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && !item.getType().isAir()) {
                try {
                    Map<String, Object> serialized = item.serialize();
                    itemsList.add(serialized);
                    successItems++;
                } catch (Exception e) {
                    plugin.getLogger().warning("[DEBUG] Failed to serialize item at index " + i + " (Type: "
                            + item.getType() + "): " + e.getMessage());
                    errorItems++;
                }
            } else {
                nullItems++;
            }
        }

        plugin.getLogger().info("[DEBUG] Item serialization summary: " +
                "input_array_length=" + items.length +
                ", successful=" + successItems +
                ", null_or_air=" + nullItems +
                ", errors=" + errorItems +
                ", output_list_size=" + itemsList.size());

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
        }, 1200, 1200);
    }
}

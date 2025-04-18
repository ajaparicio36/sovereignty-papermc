package com.tatayless.sovereignty.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Nation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
import java.util.stream.Collectors;

public class VaultService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, NationVault> nationVaults = new HashMap<>();
    private final Map<Integer, String> entityToVault = new HashMap<>(); // Maps entity ID to vault ID
    private final Map<UUID, VaultSession> playerSessions = new HashMap<>(); // Tracks player vault sessions
    private final Gson gson = new Gson();

    public static final int MAX_SINGLE_PAGE_SIZE = 54; // 6 rows
    private static final int NEXT_PAGE_SLOT = 53; // Last slot in inventory (6th row, 9th column)
    private static final int PREV_PAGE_SLOT = 45; // First slot in last row (6th row, 1st column)

    public VaultService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
    }

    public void loadVaults() {
        CompletableFuture.runAsync(() -> {
            plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
                @Override
                public Void execute(Connection conn, DSLContext context) throws SQLException {
                    // Load nation vaults
                    Result<Record> results = context.select().from("nation_vaults").fetch();

                    for (Record record : results) {
                        String id = record.get("id", String.class);
                        String nationId = record.get("nation_id", String.class);
                        String itemsJson = record.get("items", String.class);
                        String overflowItemsJson = record.get("overflow_items", String.class);
                        Object overflowExpiryObj = record.get("overflow_expiry");

                        Map<Integer, ItemStack[]> vaultPages = new HashMap<>();
                        ItemStack[] overflowItems = null;
                        Date overflowExpiry = null;

                        if (itemsJson != null && !itemsJson.isEmpty()) {
                            try {
                                // First check if the format is the new map-based format
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
                                // If this fails, try the old format (single array of items)
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
                                // SQLite dates are stored as strings
                                LocalDateTime ldt = LocalDateTime.parse((String) overflowExpiryObj);
                                overflowExpiry = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                            }
                        }

                        NationVault vault = new NationVault(id, nationId, vaultPages, overflowItems, overflowExpiry);
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

                    return null;
                }
            });

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
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            return;
        }

        getOrCreateVault(nationId).thenAccept(vault -> {
            if (vault != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Open the first page of the vault
                    openVaultPage(player, vault, 0);

                    // Create a session for the player
                    playerSessions.put(player.getUniqueId(), new VaultSession(nationId, 0));
                });
            } else {
                player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            }
        });
    }

    private void openVaultPage(Player player, NationVault vault, int page) {
        Nation nation = nationService.getNation(vault.getNationId());
        if (nation == null)
            return;

        // Calculate the vault size based on power level
        int baseRows = plugin.getConfigManager().getBaseVaultRows();
        int additionalRows = plugin.getConfigManager().getAdditionalRowsPerPowerLevel() * (nation.getPowerLevel() - 1);
        int totalRows = Math.min(6, baseRows + additionalRows); // Cap at 6 rows per page
        int size = totalRows * 9;

        Inventory inventory = Bukkit.createInventory(null, size,
                net.kyori.adventure.text.Component
                        .text("Nation Vault: " + nation.getName() + " (Page " + (page + 1) + ")"));

        // Get items for this page
        ItemStack[] pageItems = vault.getPageItems(page);
        if (pageItems != null) {
            // Copy items to avoid modifying the stored array
            ItemStack[] displayItems = new ItemStack[size];
            System.arraycopy(pageItems, 0, displayItems, 0, Math.min(pageItems.length, size));

            // Don't overwrite navigation buttons
            if (page > 0 && size > PREV_PAGE_SLOT) {
                displayItems[PREV_PAGE_SLOT] = createNavigationItem(Material.ARROW, "Previous Page");
            }

            // Check if we need a next page button (if there are more pages or overflow
            // items)
            int maxPages = calculateMaxPages(nation.getPowerLevel());
            if ((page < maxPages - 1 && vault.hasPage(page + 1)) ||
                    (page == maxPages - 1 && vault.hasOverflow())) {
                if (size > NEXT_PAGE_SLOT) {
                    displayItems[NEXT_PAGE_SLOT] = createNavigationItem(Material.ARROW, "Next Page");
                }
            }

            inventory.setContents(displayItems);
        } else {
            // Add navigation buttons for empty pages if needed
            if (page > 0) {
                inventory.setItem(PREV_PAGE_SLOT, createNavigationItem(Material.ARROW, "Previous Page"));
            }

            int maxPages = calculateMaxPages(nation.getPowerLevel());
            if (page < maxPages - 1) {
                inventory.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.ARROW, "Next Page"));
            }
        }

        player.openInventory(inventory);
    }

    private ItemStack createNavigationItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(name)
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Click to navigate")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private int calculateMaxPages(int powerLevel) {
        return Math.min(plugin.getConfigManager().getMaxVaultPages(), powerLevel);
    }

    public CompletableFuture<NationVault> getOrCreateVault(String nationId) {
        // Check if vault exists in memory
        NationVault vault = nationVaults.get(nationId);
        if (vault != null) {
            return CompletableFuture.completedFuture(vault);
        }

        // Create new vault
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<NationVault>() {
                @Override
                public NationVault execute(Connection conn, DSLContext context) throws SQLException {
                    // Check if vault exists in DB
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
                                // Try new format first (map of pages)
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
                                // Fall back to old format (single array)
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
                                // SQLite dates are stored as strings
                                LocalDateTime ldt = LocalDateTime.parse((String) overflowExpiryObj);
                                overflowExpiry = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                            }
                        }

                        NationVault existingVault = new NationVault(id, nationId, vaultPages, overflowItems,
                                overflowExpiry);
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

                    NationVault newVault = new NationVault(vaultId, nationId, new HashMap<>(), null, null);
                    nationVaults.put(nationId, newVault);
                    return newVault;
                }
            });

        });
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUuid = player.getUniqueId();

        // Check if player has an active vault session
        if (!playerSessions.containsKey(playerUuid))
            return;

        VaultSession session = playerSessions.get(playerUuid);
        String title = event.getView().title().toString();

        // Verify this is a vault inventory
        if (!title.startsWith("Nation Vault:"))
            return;

        // Handle navigation buttons
        if (event.getSlot() == NEXT_PAGE_SLOT && event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.ARROW) {
            event.setCancelled(true);
            navigateVault(player, session.getNationId(), session.getPage() + 1);
            return;
        }

        if (event.getSlot() == PREV_PAGE_SLOT && event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.ARROW) {
            event.setCancelled(true);
            navigateVault(player, session.getNationId(), session.getPage() - 1);
            return;
        }
    }

    private void navigateVault(Player player, String nationId, int newPage) {
        NationVault vault = nationVaults.get(nationId);
        if (vault == null)
            return;

        VaultSession session = playerSessions.get(player.getUniqueId());

        // Update the current page inventory before switching
        updateVaultPage(player, vault, session.getPage());

        // Open the new page
        openVaultPage(player, vault, newPage);

        // Update the session
        session.setPage(newPage);
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Check if player has an active vault session
        if (!playerSessions.containsKey(playerUuid))
            return;

        VaultSession session = playerSessions.get(playerUuid);
        String title = event.getView().title().toString();

        // Verify this is a vault inventory
        if (!title.startsWith("Nation Vault:"))
            return;

        // Update the vault with the contents of this inventory
        NationVault vault = nationVaults.get(session.getNationId());
        if (vault != null) {
            updateVaultPage(player, vault, session.getPage());
        }

        // Remove the session when the player closes the last vault inventory
        if (!player.getOpenInventory().title().toString().startsWith("Nation Vault:")) {
            playerSessions.remove(playerUuid);
        }
    }

    private void updateVaultPage(Player player, NationVault vault, int page) {
        Inventory inventory = player.getOpenInventory().getTopInventory();

        // Create a copy of the inventory contents (except navigation buttons)
        ItemStack[] contents = inventory.getContents().clone();

        // Remove navigation buttons
        int size = contents.length;
        if (size > PREV_PAGE_SLOT && contents[PREV_PAGE_SLOT] != null &&
                contents[PREV_PAGE_SLOT].getType() == Material.ARROW) {
            contents[PREV_PAGE_SLOT] = null;
        }

        if (size > NEXT_PAGE_SLOT && contents[NEXT_PAGE_SLOT] != null &&
                contents[NEXT_PAGE_SLOT].getType() == Material.ARROW) {
            contents[NEXT_PAGE_SLOT] = null;
        }

        // Update the vault with the new contents
        vault.setPageItems(page, contents);

        // Save the vault asynchronously
        saveVault(vault);
    }

    public CompletableFuture<Boolean> saveVault(NationVault vault) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Serialize vault pages as map
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

                    // Update vault
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

    public CompletableFuture<Boolean> createOrMoveVaultNPC(String nationId, Location location, String playerId) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null || !nation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        return getOrCreateVault(nationId).thenCompose(vault -> {
            if (vault == null) {
                return CompletableFuture.completedFuture(false);
            }

            // Check if the nation already has an NPC
            Integer existingEntityId = getVaultNPCEntityId(nationId);

            if (existingEntityId != null) {
                // Move the existing NPC to the new location
                return moveVaultNPC(existingEntityId, location);
            } else {
                // Create a new NPC
                return createNewVaultNPC(vault, nationId, location);
            }
        });
    }

    private CompletableFuture<Boolean> moveVaultNPC(int entityId, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<Void> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                            .flatMap(world -> world.getEntities().stream())
                            .collect(Collectors.toList())) {
                        if (entity.getEntityId() == entityId && entity instanceof Villager) {
                            entity.teleport(location);
                            break;
                        }
                    }
                    future.complete(null);
                });
                future.get(); // Wait for task to complete

                // Update location in database
                String vaultId = entityToVault.get(entityId);
                if (vaultId != null) {
                    plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                        @Override
                        public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                            String locationStr = String.format("%f,%f,%f,%s",
                                    location.getX(), location.getY(), location.getZ(),
                                    location.getWorld().getName());

                            context.update(DSL.table("vault_npcs"))
                                    .set(DSL.field("coordinates"), locationStr)
                                    .where(DSL.field("entity_id").eq(entityId))
                                    .execute();

                            return true;
                        }
                    });
                }

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to move vault NPC: " + e.getMessage());
                return false;
            }
        });
    }

    private CompletableFuture<Boolean> createNewVaultNPC(NationVault vault, String nationId, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    Nation nation = nationService.getNation(nationId);
                    if (nation == null)
                        return false;

                    // Create the NPC entity
                    final Villager[] npc = { null };
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        npc[0] = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);

                        // Create stylized name for the vault NPC
                        net.kyori.adventure.text.Component nameComponent = net.kyori.adventure.text.Component
                                .text(nation.getName() + " ")
                                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                                .append(net.kyori.adventure.text.Component
                                        .text("Vault")
                                        .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));

                        npc[0].customName(nameComponent);
                        npc[0].setCustomNameVisible(true);
                        npc[0].setProfession(Villager.Profession.LIBRARIAN);
                        npc[0].setAI(false);
                        npc[0].setInvulnerable(true);
                        npc[0].setSilent(true);
                    });

                    // Wait for entity to be created
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Thread interrupted", e);
                    }

                    // Store the NPC in database
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
                }
            });
        });
    }

    public CompletableFuture<Boolean> removeVaultNPC(String nationId) {
        Integer entityId = getVaultNPCEntityId(nationId);
        if (entityId == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Remove the entity
                CompletableFuture<Void> future = new CompletableFuture<>();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                            .flatMap(world -> world.getEntities().stream())
                            .collect(Collectors.toList())) {
                        if (entity.getEntityId() == entityId) {
                            entity.remove();
                            break;
                        }
                    }
                    future.complete(null);
                });
                future.get(); // Wait for task to complete

                // Remove from database
                plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                    @Override
                    public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                        context.deleteFrom(DSL.table("vault_npcs"))
                                .where(DSL.field("nation_id").eq(nationId))
                                .execute();
                        return true;
                    }
                });

                // Remove from tracking map
                entityToVault.remove(entityId);

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to remove vault NPC: " + e.getMessage());
                return false;
            }
        });
    }

    public Integer getVaultNPCEntityId(String nationId) {
        for (Map.Entry<Integer, String> entry : entityToVault.entrySet()) {
            String vaultId = entry.getValue();
            NationVault vault = findVaultById(vaultId);
            if (vault != null && vault.getNationId().equals(nationId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private NationVault findVaultById(String vaultId) {
        for (NationVault vault : nationVaults.values()) {
            if (vault.getId().equals(vaultId)) {
                return vault;
            }
        }
        return null;
    }

    public boolean hasVaultNPC(String nationId) {
        return getVaultNPCEntityId(nationId) != null;
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
        private Map<Integer, ItemStack[]> pages;
        private ItemStack[] overflowItems;
        private Date overflowExpiry;

        public NationVault(String id, String nationId, Map<Integer, ItemStack[]> pages,
                ItemStack[] overflowItems, Date overflowExpiry) {
            this.id = id;
            this.nationId = nationId;
            this.pages = pages != null ? pages : new HashMap<>();
            this.overflowItems = overflowItems;
            this.overflowExpiry = overflowExpiry;
        }

        public String getId() {
            return id;
        }

        public String getNationId() {
            return nationId;
        }

        public Map<Integer, ItemStack[]> getPages() {
            return pages;
        }

        public ItemStack[] getPageItems(int page) {
            return pages.get(page);
        }

        public void setPageItems(int page, ItemStack[] items) {
            pages.put(page, items);
        }

        public boolean hasPage(int page) {
            return pages.containsKey(page) && pages.get(page) != null;
        }

        public ItemStack[] getOverflowItems() {
            return overflowItems;
        }

        public boolean hasOverflow() {
            return overflowItems != null && overflowItems.length > 0;
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

        public void addOverflowItems(List<ItemStack> items, int expiryMinutes) {
            if (items == null || items.isEmpty())
                return;

            List<ItemStack> allOverflow = new ArrayList<>();

            // Add existing overflow items
            if (overflowItems != null) {
                Collections.addAll(allOverflow, Arrays.stream(overflowItems)
                        .filter(Objects::nonNull)
                        .toArray(ItemStack[]::new));
            }

            // Add new overflow items
            allOverflow.addAll(items);

            // Set the overflow items
            this.overflowItems = allOverflow.toArray(new ItemStack[0]);

            // Set expiry time
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, expiryMinutes);
            this.overflowExpiry = calendar.getTime();
        }
    }

    public static class VaultSession {
        private final String nationId;
        private int page;

        public VaultSession(String nationId, int page) {
            this.nationId = nationId;
            this.page = page;
        }

        public String getNationId() {
            return nationId;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }
    }
}

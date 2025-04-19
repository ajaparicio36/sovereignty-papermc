package com.tatayless.sovereignty.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Nation;
import org.bukkit.*;
import org.bukkit.entity.Entity;
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
import java.util.concurrent.TimeUnit;

public class VaultService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, NationVault> nationVaults = new HashMap<>();
    private final Map<Integer, String> entityToVault = new HashMap<>(); // Maps entity ID to vault ID
    private final Map<UUID, VaultSession> playerSessions = new HashMap<>(); // Tracks player vault sessions
    private final Gson gson = new Gson();

    public static final int MAX_SINGLE_PAGE_SIZE = 54; // 6 rows
    public static final int NEXT_PAGE_SLOT = 53; // Last slot in inventory (6th row, 9th column)
    public static final int PREV_PAGE_SLOT = 45; // First slot in last row (6th row, 1st column)

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

                        NationVault vault = new NationVault(id, nationId, vaultPages, overflowItems, overflowExpiry);
                        nationVaults.put(nationId, vault);
                    }

                    Result<Record> npcResults = context.select().from("vault_npcs").fetch();

                    for (Record record : npcResults) {
                        int entityId = record.get("entity_id", Integer.class);
                        String vaultId = record.get("nation_vault_id", String.class);
                        entityToVault.put(entityId, vaultId);
                    }

                    plugin.getLogger().info("Loaded " + nationVaults.size() + " nation vaults from database");

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
        }.runTaskTimerAsynchronously(plugin, 1200, 1200);
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
                    openVaultPage(player, vault, 0);
                    playerSessions.put(player.getUniqueId(), new VaultSession(nationId, 0));
                });
            } else {
                player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            }
        });
    }

    public void openVaultPage(Player player, String nationId, int page) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            return;
        }

        getOrCreateVault(nationId).thenAccept(vault -> {
            if (vault != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int maxPages = calculateMaxPages(nation.getPowerLevel());
                    int adjustedPage = page;
                    if (adjustedPage >= maxPages) {
                        adjustedPage = maxPages - 1;
                    }
                    if (adjustedPage < 0) {
                        adjustedPage = 0;
                    }

                    openVaultPage(player, vault, adjustedPage);
                    playerSessions.put(player.getUniqueId(), new VaultSession(nationId, adjustedPage));
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

        int baseRows = plugin.getConfigManager().getBaseVaultRows();
        int additionalRows = plugin.getConfigManager().getAdditionalRowsPerPowerLevel() * (nation.getPowerLevel() - 1);
        int totalRows = Math.min(6, baseRows + additionalRows);
        int size = totalRows * 9;

        Inventory inventory = Bukkit.createInventory(null, size,
                net.kyori.adventure.text.Component
                        .text("Nation Vault: " + nation.getName() + " (Page " + (page + 1) + ")"));

        ItemStack[] pageItems = vault.getPageItems(page);
        if (pageItems != null) {
            for (int i = 0; i < Math.min(pageItems.length, size); i++) {
                if ((i == PREV_PAGE_SLOT || i == NEXT_PAGE_SLOT) && size - i <= 9) {
                    continue;
                }
                inventory.setItem(i, pageItems[i]);
            }
        }

        if (totalRows == 6) {
            if (page > 0) {
                inventory.setItem(PREV_PAGE_SLOT, createNavigationItem(Material.ARROW, "Previous Page"));
            }

            int maxPages = calculateMaxPages(nation.getPowerLevel());
            if ((page < maxPages - 1) || (page == maxPages - 1 && vault.hasOverflow())) {
                inventory.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.ARROW, "Next Page"));
            }
        }

        player.openInventory(inventory);

        plugin.getVaultUpdateManager().registerNationVaultViewer(vault.getId(), player.getUniqueId(), page);
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
        NationVault vault = nationVaults.get(nationId);
        if (vault != null) {
            return CompletableFuture.completedFuture(vault);
        }

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<NationVault>() {
                @Override
                public NationVault execute(Connection conn, DSLContext context) throws SQLException {
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

                        NationVault existingVault = new NationVault(id, nationId, vaultPages, overflowItems,
                                overflowExpiry);
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

        if (!playerSessions.containsKey(playerUuid))
            return;

        VaultSession session = playerSessions.get(playerUuid);
        String title = event.getView().title().toString();

        if (!title.startsWith("Nation Vault:"))
            return;

        if ((event.getSlot() == NEXT_PAGE_SLOT || event.getSlot() == PREV_PAGE_SLOT)) {
            if (isNavigationButton(event.getCurrentItem()) && event.getClick().isLeftClick()) {
                event.setCancelled(true);

                if (event.getSlot() == NEXT_PAGE_SLOT) {
                    navigateVault(player, session.getNationId(), session.getPage() + 1);
                } else {
                    navigateVault(player, session.getNationId(), session.getPage() - 1);
                }
            }
            event.setCancelled(true);
            return;
        }

        if (isNavigationButton(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        if (!event.isCancelled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                NationVault vault = nationVaults.get(session.getNationId());
                if (vault != null) {
                    updateVaultPage(player, vault, session.getPage());
                    plugin.getVaultUpdateManager().updateNationVaultViewers(
                            vault.getId(),
                            session.getPage(),
                            player.getUniqueId(),
                            event.getInventory());
                }
            });
        }
    }

    private boolean isNavigationButton(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW)
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return false;
        String name = meta.displayName().toString();
        return name != null && (name.contains("Previous Page") || name.contains("Next Page"));
    }

    private void navigateVault(Player player, String nationId, int newPage) {
        NationVault vault = nationVaults.get(nationId);
        if (vault == null)
            return;

        VaultSession session = playerSessions.get(player.getUniqueId());

        plugin.getVaultUpdateManager().unregisterNationVaultViewer(vault.getId(), player.getUniqueId(),
                session.getPage());

        updateVaultPage(player, vault, session.getPage());

        openVaultPage(player, vault, newPage);

        session.setPage(newPage);
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        if (!playerSessions.containsKey(playerUuid))
            return;

        VaultSession session = playerSessions.get(playerUuid);
        String title = event.getView().title().toString();

        if (!title.startsWith("Nation Vault:"))
            return;

        NationVault vault = nationVaults.get(session.getNationId());
        if (vault != null) {
            updateVaultPage(player, vault, session.getPage());
            saveVault(vault).thenAccept(success -> {
                if (success) {
                    plugin.getLogger().info("Vault for nation " + session.getNationId() +
                            " page " + session.getPage() + " saved successfully");
                } else {
                    plugin.getLogger().warning("Failed to save vault for nation " +
                            session.getNationId() + " page " + session.getPage());
                }
            });
        }

        plugin.getVaultUpdateManager().unregisterNationVaultViewer(vault.getId(), player.getUniqueId(),
                session.getPage());

        if (!player.getOpenInventory().title().toString().startsWith("Nation Vault:")) {
            playerSessions.remove(playerUuid);
        }
    }

    private void updateVaultPage(Player player, NationVault vault, int page) {
        Inventory inventory = player.getOpenInventory().getTopInventory();

        ItemStack[] contents = new ItemStack[inventory.getSize()];

        for (int i = 0; i < contents.length; i++) {
            if ((i == PREV_PAGE_SLOT || i == NEXT_PAGE_SLOT) &&
                    ((inventory.getSize() - i <= 9) && isNavigationButton(inventory.getItem(i)))) {
                continue;
            }
            contents[i] = inventory.getItem(i);
        }

        vault.setPageItems(page, contents);

        saveVault(vault);
    }

    public CompletableFuture<Boolean> saveVault(NationVault vault) {
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

    private ItemStack[] deserializeItems(List<Map<String, Object>> itemsList) {
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

    public String getVaultIdFromEntity(int entityId) {
        return entityToVault.get(entityId);
    }

    public CompletableFuture<Boolean> removeVaultNPC(String nationId) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Find entity IDs associated with this nation's vault
                    List<Integer> entityIds = new ArrayList<>();

                    for (Map.Entry<Integer, String> entry : entityToVault.entrySet()) {
                        NationVault vault = nationVaults.get(nationId);
                        if (vault != null && entry.getValue().equals(vault.getId())) {
                            entityIds.add(entry.getKey());
                        }
                    }

                    // Delete from database
                    context.deleteFrom(DSL.table("vault_npcs"))
                            .where(DSL.field("nation_vault_id").eq(
                                    context.select(DSL.field("id"))
                                            .from("nation_vaults")
                                            .where(DSL.field("nation_id").eq(nationId))))
                            .execute();

                    // Remove from memory
                    for (Integer entityId : entityIds) {
                        entityToVault.remove(entityId);

                        // Find and remove the entity from the world
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (org.bukkit.World world : Bukkit.getWorlds()) {
                                for (Entity entity : world.getEntities()) {
                                    if (entity.getEntityId() == entityId && entity instanceof Villager) {
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

    public CompletableFuture<Boolean> createOrMoveVaultNPC(String nationId, Location location, String playerId) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Find the vault ID for this nation
                    Record vaultRecord = context.select(DSL.field("id"))
                            .from("nation_vaults")
                            .where(DSL.field("nation_id").eq(nationId))
                            .fetchOne();

                    if (vaultRecord == null) {
                        // Create a new vault if it doesn't exist
                        String vaultId = UUID.randomUUID().toString();
                        context.insertInto(
                                DSL.table("nation_vaults"),
                                DSL.field("id"),
                                DSL.field("nation_id")).values(
                                        vaultId,
                                        nationId)
                                .execute();

                        // Get or create the vault in memory
                        getOrCreateVault(nationId);

                        vaultRecord = context.select(DSL.field("id"))
                                .from("nation_vaults")
                                .where(DSL.field("id").eq(vaultId))
                                .fetchOne();
                    }

                    final String vaultId = vaultRecord.get(0, String.class);

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

                            // Configure the villager
                            villager.setProfession(Villager.Profession.LIBRARIAN);
                            villager.customName(net.kyori.adventure.text.Component.text("Nation Vault")
                                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
                            villager.setCustomNameVisible(true);
                            villager.setAI(false);
                            villager.setInvulnerable(true);
                            villager.setSilent(true);

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
            ItemStack[] pageItems = pages.get(page);
            if (pageItems == null) {
                pageItems = new ItemStack[MAX_SINGLE_PAGE_SIZE];
                pages.put(page, pageItems);
            }
            return pageItems;
        }

        public void setPageItems(int page, ItemStack[] items) {
            if (items != null) {
                ItemStack[] copy = new ItemStack[items.length];
                System.arraycopy(items, 0, copy, 0, items.length);
                pages.put(page, copy);
            }
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

            if (overflowItems != null) {
                Collections.addAll(allOverflow, Arrays.stream(overflowItems)
                        .filter(Objects::nonNull)
                        .toArray(ItemStack[]::new));
            }

            allOverflow.addAll(items);

            this.overflowItems = allOverflow.toArray(new ItemStack[0]);

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

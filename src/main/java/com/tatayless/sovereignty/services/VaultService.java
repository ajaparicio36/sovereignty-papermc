package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.services.vault.VaultNPCManager;
import com.tatayless.sovereignty.services.vault.VaultStorageManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class VaultService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, NationVault> nationVaults = new HashMap<>();
    private final Map<UUID, VaultSession> playerSessions = new HashMap<>();

    // New modular components
    private final VaultNPCManager npcManager;
    private final VaultStorageManager storageManager;

    public static final int MAX_SINGLE_PAGE_SIZE = 54; // 6 rows
    public static final int NEXT_PAGE_SLOT = 53; // Last slot in inventory (6th row, 9th column)
    public static final int PREV_PAGE_SLOT = 45; // First slot in last row (6th row, 1st column)

    public VaultService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.npcManager = new VaultNPCManager(plugin);
        this.storageManager = new VaultStorageManager(plugin);
    }

    /**
     * Initializes the vault service
     */
    public void initialize() {
        loadVaults();
        registerEventHandlers();
    }

    /**
     * Registers all event handlers for the vault service
     */
    public void registerEventHandlers() {
        // Register the VaultListener to handle inventory events
        plugin.getServer().getPluginManager().registerEvents(
                new com.tatayless.sovereignty.listeners.VaultListener(plugin, this),
                plugin);

        plugin.getLogger().info("Registered vault event handlers");
    }

    /**
     * Loads vaults and respawns NPCs
     */
    public void loadVaults() {
        // Load all vault data from the database
        for (Nation nation : nationService.getNations().values()) {
            storageManager.getOrCreateVault(nation.getId(), nationVaults);
        }

        // Schedule overflow cleanup
        storageManager.scheduleOverflowCleanup(nationVaults);

        // Load and respawn all NPCs
        npcManager.loadAndRespawnNPCs();

        plugin.getLogger().info("Vault service initialized with " + nationVaults.size() + " vaults");
    }

    public void openVault(Player player, String nationId) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            return;
        }

        storageManager.getOrCreateVault(nationId, nationVaults).thenAccept(vault -> {
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

        storageManager.getOrCreateVault(nationId, nationVaults).thenAccept(vault -> {
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
        return storageManager.getOrCreateVault(nationId, nationVaults);
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
            // Schedule an update task after the click is processed
            Bukkit.getScheduler().runTask(plugin, () -> {
                updateAndSaveVaultForPlayer(player);
            });
        }
    }

    /**
     * Updates and saves the vault for a player
     */
    public void updateAndSaveVaultForPlayer(Player player) {
        UUID playerUuid = player.getUniqueId();
        if (!playerSessions.containsKey(playerUuid))
            return;

        VaultSession session = playerSessions.get(playerUuid);
        NationVault vault = nationVaults.get(session.getNationId());

        if (vault != null) {
            updateVaultPage(player, vault, session.getPage());
            plugin.getVaultUpdateManager().updateNationVaultViewers(
                    vault.getId(),
                    session.getPage(),
                    playerUuid,
                    player.getOpenInventory().getTopInventory());
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
            storageManager.saveVault(vault).thenAccept(success -> {
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

        storageManager.saveVault(vault);
    }

    /**
     * Clean up a player's session when they quit or disconnect
     */
    public void cleanupPlayerSession(UUID playerUuid) {
        if (playerSessions.containsKey(playerUuid)) {
            playerSessions.remove(playerUuid);
        }
    }

    public String getVaultIdFromEntity(int entityId) {
        return npcManager.getVaultIdFromEntity(entityId);
    }

    public CompletableFuture<Boolean> removeVaultNPC(String nationId) {
        NationVault vault = nationVaults.get(nationId);
        if (vault == null) {
            return CompletableFuture.completedFuture(false);
        }

        return npcManager.removeVaultNPC(nationId, vault.getId());
    }

    public CompletableFuture<Boolean> createOrMoveVaultNPC(String nationId, Location location, String playerId) {
        return storageManager.getOrCreateVault(nationId, nationVaults)
                .thenCompose(vault -> {
                    if (vault != null) {
                        return npcManager.createOrMoveVaultNPC(nationId, vault.getId(), location, playerId);
                    } else {
                        return CompletableFuture.completedFuture(false);
                    }
                });
    }

    /**
     * Saves a vault to the database
     * 
     * @param vault The vault to save
     * @return A CompletableFuture that completes with true if save was successful
     */
    public CompletableFuture<Boolean> saveVault(NationVault vault) {
        return storageManager.saveVault(vault);
    }

    // Inner classes stay unchanged
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

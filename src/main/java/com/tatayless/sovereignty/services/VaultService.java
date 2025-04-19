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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class VaultService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, NationVault> nationVaults = new HashMap<>();
    private final Map<UUID, VaultSession> playerSessions = new ConcurrentHashMap<>();

    private final VaultNPCManager npcManager;
    private final VaultStorageManager storageManager;

    public static final int MAX_SINGLE_PAGE_SIZE = 54;
    public static final int NEXT_PAGE_SLOT = 53;
    public static final int PREV_PAGE_SLOT = 45;

    public VaultService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.npcManager = new VaultNPCManager(plugin);
        this.storageManager = new VaultStorageManager(plugin);
    }

    public void initialize() {
        plugin.getLogger().info("Initializing VaultService...");
        loadVaults();
        plugin.getLogger().info("VaultService initialization complete");
    }

    public void loadVaults() {
        for (Nation nation : nationService.getNations().values()) {
            storageManager.getOrCreateVault(nation.getId(), nationVaults);
        }

        storageManager.scheduleOverflowCleanup(nationVaults);
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
        UUID playerUuid = player.getUniqueId();
        VaultSession currentSession = playerSessions.get(playerUuid);

        if (currentSession != null) {
            plugin.getVaultUpdateManager().unregisterNationVaultViewer(
                    vault.getId(), playerUuid, currentSession.getPage());

            if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null) {
                if (player.getOpenInventory().title().toString().startsWith("Nation Vault:")) {
                    plugin.getLogger().info("[DEBUG] Saving current page before opening new one");
                    updateVaultPageFromInventory(player.getOpenInventory().getTopInventory(),
                            vault, currentSession.getPage());
                }
            }
        }

        Nation nation = nationService.getNation(vault.getNationId());
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.error.nation-not-found"));
            return;
        }

        int maxPages = calculateMaxPages(nation.getPowerLevel());
        if (page < 0)
            page = 0;
        if (page >= maxPages)
            page = maxPages - 1;

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
                if ((i == PREV_PAGE_SLOT || i == NEXT_PAGE_SLOT) && totalRows == 6)
                    continue;

                inventory.setItem(i, pageItems[i]);
            }
        }

        if (totalRows == 6) {
            if (page > 0) {
                inventory.setItem(PREV_PAGE_SLOT, createNavigationItem(Material.ARROW, "Previous Page"));
                plugin.getLogger().info("[DEBUG] Added previous page button");
            }

            if (page < maxPages - 1) {
                inventory.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.ARROW, "Next Page"));
                plugin.getLogger().info("[DEBUG] Added next page button");
            }
        }

        playerSessions.put(playerUuid, new VaultSession(vault.getNationId(), page));
        plugin.getVaultUpdateManager().registerNationVaultViewer(vault.getId(), playerUuid, page);

        player.openInventory(inventory);
        plugin.getLogger()
                .info("[DEBUG] Opened vault " + vault.getId() + " page " + page + " for player " + player.getName());
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

    public void navigateToNextPage(Player player) {
        UUID playerUuid = player.getUniqueId();

        plugin.getLogger().info("[DEBUG] Starting navigation to next page for " + player.getName());

        if (!playerSessions.containsKey(playerUuid)) {
            plugin.getLogger().warning(
                    "[DEBUG] No active session found for player " + player.getName() + " during next page navigation");
            return;
        }

        VaultSession session = playerSessions.get(playerUuid);
        plugin.getLogger()
                .info("[DEBUG] Current session - Nation: " + session.getNationId() + ", Page: " + session.getPage());

        int nextPage = session.getPage() + 1;
        plugin.getLogger().info("[DEBUG] Attempting to navigate to page " + nextPage);

        openVaultPage(player, session.getNationId(), nextPage);
    }

    public void navigateToPreviousPage(Player player) {
        UUID playerUuid = player.getUniqueId();

        plugin.getLogger().info("[DEBUG] Starting navigation to previous page for " + player.getName());

        if (!playerSessions.containsKey(playerUuid)) {
            plugin.getLogger().warning("[DEBUG] No active session found for player " + player.getName()
                    + " during previous page navigation");
            return;
        }

        VaultSession session = playerSessions.get(playerUuid);
        plugin.getLogger()
                .info("[DEBUG] Current session - Nation: " + session.getNationId() + ", Page: " + session.getPage());

        int prevPage = session.getPage() - 1;
        if (prevPage < 0) {
            plugin.getLogger().info("[DEBUG] Cannot navigate to page " + prevPage + " (before page 0)");
            return;
        }

        plugin.getLogger().info("[DEBUG] Attempting to navigate to page " + prevPage);
        openVaultPage(player, session.getNationId(), prevPage);
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUuid = player.getUniqueId();

        if (!playerSessions.containsKey(playerUuid)) {
            plugin.getLogger().info("[DEBUG] Ignoring click - player not in a vault session: " + player.getName());
            return;
        }

        plugin.getLogger().info("[DEBUG] Scheduling vault update after click for player: " + player.getName());
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateAndSaveVaultForPlayer(player);
        });
    }

    public void updateAndSaveVaultForPlayer(Player player) {
        UUID playerUuid = player.getUniqueId();

        plugin.getLogger().info("[DEBUG] Updating and saving vault for " + player.getName());

        VaultSession session = playerSessions.get(playerUuid);
        if (session == null) {
            plugin.getLogger().warning("[DEBUG] No session found for " + player.getName());
            return;
        }

        InventoryView view = player.getOpenInventory();
        if (view == null) {
            plugin.getLogger().warning("[DEBUG] No open inventory for " + player.getName());
            return;
        }

        String title = view.title().toString();
        if (!title.startsWith("Nation Vault:")) {
            plugin.getLogger().warning("[DEBUG] Player not viewing a vault: " + title);
            return;
        }

        NationVault vault = nationVaults.get(session.getNationId());
        if (vault == null) {
            plugin.getLogger().severe("[DEBUG] Vault not found for nation " + session.getNationId());
            return;
        }

        boolean changed = updateVaultPageFromInventory(view.getTopInventory(), vault, session.getPage());

        if (changed) {
            plugin.getLogger().info("[DEBUG] Vault contents changed, saving...");
            saveVault(vault).thenAccept(success -> {
                plugin.getLogger().info("[DEBUG] Vault save result: " + success);
                if (success) {
                    plugin.getVaultUpdateManager().updateNationVaultViewers(
                            vault.getId(),
                            session.getPage(),
                            playerUuid,
                            view.getTopInventory());
                }
            });
        } else {
            plugin.getLogger().info("[DEBUG] No changes detected in vault");
        }
    }

    private boolean updateVaultPageFromInventory(Inventory inventory, NationVault vault, int page) {
        try {
            int size = inventory.getSize();
            plugin.getLogger().info("[DEBUG] Updating vault page " + page + " with inventory of size " + size);

            ItemStack[] currentContents = vault.getPageItems(page);
            if (currentContents == null || currentContents.length != size) {
                currentContents = new ItemStack[size];
                plugin.getLogger().info("[DEBUG] Created new item array for page " + page);
            }

            ItemStack[] newContents = new ItemStack[size];
            boolean changed = false;

            for (int i = 0; i < size; i++) {
                ItemStack itemInInventory = inventory.getItem(i);

                if ((i == PREV_PAGE_SLOT || i == NEXT_PAGE_SLOT) && isNavigationButton(itemInInventory)) {
                    newContents[i] = null;
                    continue;
                }

                newContents[i] = itemInInventory != null ? itemInInventory.clone() : null;

                boolean slotChanged = !Objects.equals(currentContents[i], newContents[i]);
                if (slotChanged) {
                    changed = true;
                    plugin.getLogger().info("[DEBUG] Change detected in slot " + i);
                }
            }

            if (changed) {
                plugin.getLogger().info("[DEBUG] Updating vault page " + page + " with new contents");
                vault.setPageItems(page, newContents);
            } else {
                plugin.getLogger().info("[DEBUG] No changes detected in vault page " + page);
            }

            return changed;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ERROR] Error updating vault: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean isNavigationButton(ItemStack item) {
        if (item == null || item.getType() != Material.ARROW)
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return false;
        String name = meta.displayName().toString().toLowerCase();
        boolean isNavButton = name.contains("previous page") || name.contains("next page");
        if (isNavButton) {
            plugin.getLogger().info("[DEBUG] Identified navigation button: " + meta.displayName());
        }
        return isNavButton;
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        VaultSession session = playerSessions.get(playerUuid);

        if (session == null) {
            plugin.getLogger().info(
                    "[DEBUG] Ignoring inventory close - no active vault session found for player: " + player.getName());
            return;
        }

        String title = event.getView().title().toString();
        if (!title.startsWith("Nation Vault:") || !title.contains("Page " + (session.getPage() + 1))) {
            plugin.getLogger().info("[DEBUG] Ignoring inventory close - closed inventory title '" + title
                    + "' does not match session page " + session.getPage() + " for player " + player.getName());
            return;
        }

        NationVault vault = nationVaults.get(session.getNationId());
        if (vault != null) {
            plugin.getLogger().info("[DEBUG] Handling inventory close for player " + player.getName() + ", saving page "
                    + session.getPage());
            try {
                boolean changed = updateVaultPageFromInventory(event.getInventory(), vault, session.getPage());
                if (changed) {
                    storageManager.saveVault(vault).thenAccept(success -> {
                        if (success) {
                            plugin.getLogger().info("[DEBUG] Vault " + vault.getId() + " page " + session.getPage()
                                    + " saved successfully on close for " + player.getName());
                            plugin.getVaultUpdateManager().updateNationVaultViewers(
                                    vault.getId(),
                                    session.getPage(),
                                    playerUuid,
                                    event.getInventory());
                        } else {
                            plugin.getLogger().warning("[DEBUG] Failed to save vault " + vault.getId() + " page "
                                    + session.getPage() + " on close for " + player.getName());
                        }
                    }).exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "[ERROR] Exception saving vault " + vault.getId()
                                + " page " + session.getPage() + " on close", ex);
                        return null;
                    });
                } else {
                    plugin.getLogger().info("[DEBUG] Vault page " + session.getPage() + " unchanged on close for "
                            + player.getName() + ". Skipping save.");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[ERROR] Error saving vault page " + session.getPage()
                        + " on close for " + player.getName() + ": " + e.getMessage(), e);
            }
        } else {
            plugin.getLogger().warning("[DEBUG] Vault object not found for nation " + session.getNationId()
                    + " during inventory close for " + player.getName());
        }

        plugin.getVaultUpdateManager().unregisterNationVaultViewer(vault != null ? vault.getId() : "unknown",
                player.getUniqueId(), session.getPage());
        playerSessions.remove(playerUuid);
        plugin.getLogger().info("[DEBUG] Removed vault session for " + player.getName() + " after closing inventory.");
    }

    public void handlePlayerQuit(UUID playerUuid) {
        VaultSession session = playerSessions.get(playerUuid);
        if (session != null) {
            Player player = Bukkit.getPlayer(playerUuid);
            plugin.getLogger()
                    .info("[DEBUG] Handling player quit for " + (player != null ? player.getName() : playerUuid)
                            + " who was in vault session (Nation: " + session.getNationId() + ", Page: "
                            + session.getPage() + ")");

            NationVault vault = nationVaults.get(session.getNationId());
            if (vault != null && player != null) {
                InventoryView openInv = player.getOpenInventory();
                String title = openInv.title().toString();
                if (title.startsWith("Nation Vault:") && title.contains("Page " + (session.getPage() + 1))) {
                    plugin.getLogger().info("[DEBUG] Player quit while viewing vault page " + session.getPage()
                            + ". Attempting final save.");
                    try {
                        boolean changed = updateVaultPageFromInventory(openInv.getTopInventory(), vault,
                                session.getPage());
                        if (changed) {
                            storageManager.saveVault(vault).thenAccept(success -> {
                                plugin.getLogger().info("[DEBUG] Vault save on quit (Page " + session.getPage()
                                        + ") result: " + success);
                            }).exceptionally(ex -> {
                                plugin.getLogger().log(Level.SEVERE,
                                        "[ERROR] Exception saving vault page " + session.getPage() + " on quit", ex);
                                return null;
                            });
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE,
                                "[ERROR] Error saving vault page " + session.getPage() + " on quit: " + e.getMessage(),
                                e);
                    }
                }
            } else if (vault == null) {
                plugin.getLogger().warning("[DEBUG] Vault object not found for nation " + session.getNationId()
                        + " during quit handling for " + playerUuid);
            }

            plugin.getVaultUpdateManager().unregisterNationVaultViewer(vault != null ? vault.getId() : "unknown",
                    playerUuid, session.getPage());
            playerSessions.remove(playerUuid);
            plugin.getLogger().info("[DEBUG] Cleaned up vault session for quitting player " + playerUuid);
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

    public CompletableFuture<Boolean> saveVault(NationVault vault) {
        return storageManager.saveVault(vault);
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
            this.pages = pages != null ? new HashMap<>(pages) : new HashMap<>();
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
            if (this.pages == null) {
                this.pages = new HashMap<>();
            }
            return pages;
        }

        public ItemStack[] getPageItems(int page) {
            return pages.computeIfAbsent(page, k -> new ItemStack[MAX_SINGLE_PAGE_SIZE]);
        }

        public void setPageItems(int page, ItemStack[] items) {
            if (items != null) {
                ItemStack[] sizedItems = new ItemStack[MAX_SINGLE_PAGE_SIZE];
                for (int i = 0; i < MAX_SINGLE_PAGE_SIZE; i++) {
                    if (i < items.length && items[i] != null) {
                        sizedItems[i] = items[i].clone();
                    } else {
                        sizedItems[i] = null;
                    }
                }
                pages.put(page, sizedItems);
            } else {
                pages.put(page, new ItemStack[MAX_SINGLE_PAGE_SIZE]);
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

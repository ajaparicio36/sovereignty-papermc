package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.services.vault.VaultNPCManager;
import com.tatayless.sovereignty.services.vault.VaultStorageManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class VaultService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, NationVault> nationVaults = new HashMap<>();
    private final Map<UUID, PlayerVaultSession> playerSessions = new ConcurrentHashMap<>();
    private int savePeriodTicks = 6000; // 5 minute auto-save by default

    private final VaultNPCManager npcManager;
    private final VaultStorageManager storageManager;
    private final NamespacedKey vaultPageKey;

    public static final int MAX_SINGLE_PAGE_SIZE = 54;
    public static final int NEXT_PAGE_SLOT = 53;
    public static final int PREV_PAGE_SLOT = 45;

    public VaultService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.npcManager = new VaultNPCManager(plugin);
        this.storageManager = new VaultStorageManager(plugin);
        this.vaultPageKey = new NamespacedKey(plugin, "vault_page");

        plugin.getLogger().info("VaultService created");
    }

    public void initialize() {
        plugin.getLogger().info("Initializing VaultService...");
        loadVaults();
        setupPeriodicSaving();
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

    private void setupPeriodicSaving() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.getLogger().info("Running periodic save of all vaults...");
            int count = 0;
            for (NationVault vault : nationVaults.values()) {
                if (vault.isDirty()) {
                    storageManager.saveVault(vault).thenAccept(success -> {
                        if (success) {
                            vault.markClean();
                        }
                    });
                    count++;
                }
            }
            plugin.getLogger().info("Periodic vault save completed: " + count + " vaults saved");
        }, savePeriodTicks, savePeriodTicks);
    }

    public void openVault(Player player, String nationId) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            return;
        }

        plugin.getLogger()
                .info("Attempting to open vault for nation: " + nationId + " and player: " + player.getName());

        storageManager.getOrCreateVault(nationId, nationVaults).thenAccept(vault -> {
            if (vault != null) {
                plugin.getLogger().info("Vault found, opening page 0 for " + player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> openVaultPage(player, vault, 0));
            } else {
                player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
                plugin.getLogger().warning("Vault not found for nation: " + nationId);
            }
        });
    }

    public void openVaultPage(Player player, String nationId, int page) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
            return;
        }

        plugin.getLogger().info("Attempting to open vault page " + page + " for nation: " + nationId);
        storageManager.getOrCreateVault(nationId, nationVaults).thenAccept(vault -> {
            if (vault != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    int maxPages = calculateMaxPages(nation.getPowerLevel());
                    int adjustedPage = Math.max(0, Math.min(page, maxPages - 1));
                    if (adjustedPage != page) {
                        plugin.getLogger().info("Adjusted requested page " + page + " to " + adjustedPage +
                                " (maxPages: " + maxPages + ")");
                    }
                    openVaultPage(player, vault, adjustedPage);
                });
            } else {
                player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
                plugin.getLogger().warning("Vault not found for nation: " + nationId);
            }
        });
    }

    private void openVaultPage(Player player, NationVault vault, int page) {
        Nation nation = nationService.getNation(vault.getNationId());
        if (nation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.error.nation-not-found"));
            return;
        }

        UUID playerId = player.getUniqueId();
        PlayerVaultSession currentSession = playerSessions.get(playerId);
        if (currentSession != null && currentSession.nationId.equals(vault.getNationId())) {
            if (player.getOpenInventory() != null) {
                Inventory topInv = player.getOpenInventory().getTopInventory();
                if (topInv != null && topInv.getHolder() instanceof VaultInventoryHolder) {
                    VaultInventoryHolder holder = (VaultInventoryHolder) topInv.getHolder();
                    if (holder.getVaultId().equals(vault.getId()) && holder.getPage() == currentSession.page) {
                        plugin.getLogger().info(
                                "Saving current page " + currentSession.page + " before switching to page " + page);
                        saveInventoryToVault(topInv, vault, currentSession.page);
                    }
                }
            }
        }

        int maxPages = calculateMaxPages(nation.getPowerLevel());
        int baseRows = plugin.getConfigManager().getBaseVaultRows();
        int additionalRows = plugin.getConfigManager().getAdditionalRowsPerPowerLevel() * (nation.getPowerLevel() - 1);
        int totalRows = Math.min(6, baseRows + additionalRows);
        int size = totalRows * 9;

        VaultInventoryHolder holder = new VaultInventoryHolder(vault.getId(), vault.getNationId(), page);
        Inventory inventory = Bukkit.createInventory(holder, size,
                net.kyori.adventure.text.Component
                        .text("Nation Vault: " + nation.getName() + " (Page " + (page + 1) + ")"));
        holder.setInventory(inventory);

        ItemStack[] pageItems = vault.getPageItems(page);
        for (int i = 0; i < Math.min(pageItems.length, size); i++) {
            if ((totalRows == 6) && (i == PREV_PAGE_SLOT || i == NEXT_PAGE_SLOT)) {
                continue;
            }
            inventory.setItem(i, pageItems[i]);
        }

        if (totalRows == 6) {
            if (page > 0) {
                inventory.setItem(PREV_PAGE_SLOT, createNavigationItem(Material.ARROW, "Previous Page", -1));
                plugin.getLogger().info("Added previous page button for page " + page);
            }
            if (page < maxPages - 1) {
                inventory.setItem(NEXT_PAGE_SLOT, createNavigationItem(Material.ARROW, "Next Page", 1));
                plugin.getLogger().info("Added next page button for page " + page);
            }
        }

        playerSessions.put(playerId, new PlayerVaultSession(vault.getNationId(), page));
        plugin.getVaultUpdateManager().registerNationVaultViewer(vault.getId(), playerId, page);

        player.openInventory(inventory);
        plugin.getLogger().info("Opened vault " + vault.getId() + " page " + page + " for player " + player.getName());
    }

    private ItemStack createNavigationItem(Material material, String name, int direction) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(name)
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Click to navigate")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(vaultPageKey, PersistentDataType.INTEGER, direction);

        item.setItemMeta(meta);
        return item;
    }

    private int calculateMaxPages(int powerLevel) {
        return Math.max(1, Math.min(plugin.getConfigManager().getMaxVaultPages(), powerLevel));
    }

    public void handleNavigationClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return;

        ItemMeta meta = item.getItemMeta();
        Integer pageOffset = meta.getPersistentDataContainer().get(vaultPageKey, PersistentDataType.INTEGER);

        if (pageOffset == null) {
            plugin.getLogger().warning("Navigation item clicked but no page data found: " + item.getType());
            return;
        }

        PlayerVaultSession session = playerSessions.get(player.getUniqueId());
        if (session == null) {
            plugin.getLogger().warning("Player clicked navigation but has no session: " + player.getName());
            return;
        }

        int newPage = session.page + pageOffset;
        plugin.getLogger().info("Player " + player.getName() + " navigating from page " + session.page +
                " to page " + newPage + " (offset: " + pageOffset + ")");

        openVaultPage(player, session.nationId, newPage);
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();

        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof VaultInventoryHolder))
            return;

        VaultInventoryHolder holder = (VaultInventoryHolder) inventory.getHolder();
        String vaultId = holder.getVaultId();
        @SuppressWarnings("unused")
        String nationId = holder.getNationId();
        int page = holder.getPage();

        plugin.getLogger().info("Player " + player.getName() + " closing vault " + vaultId + " page " + page);

        NationVault vault = null;
        for (NationVault v : nationVaults.values()) {
            if (v.getId().equals(vaultId)) {
                vault = v;
                break;
            }
        }

        if (vault == null) {
            plugin.getLogger()
                    .warning("Could not find vault " + vaultId + " when handling close for " + player.getName());
            return;
        }

        saveInventoryToVault(inventory, vault, page);

        playerSessions.remove(playerId);
        plugin.getVaultUpdateManager().unregisterNationVaultViewer(vaultId, playerId, page);
        plugin.getLogger().info("Cleaned up vault session for " + player.getName());
    }

    private void saveInventoryToVault(Inventory inventory, NationVault vault, int page) {
        plugin.getLogger().info("Saving inventory contents to vault " + vault.getId() + " page " + page);

        int size = inventory.getSize();
        ItemStack[] currentItems = vault.getPageItems(page);
        ItemStack[] newItems = new ItemStack[MAX_SINGLE_PAGE_SIZE];
        boolean changed = false;

        for (int i = 0; i < size; i++) {
            if ((i == PREV_PAGE_SLOT || i == NEXT_PAGE_SLOT) && (size == 54)) {
                ItemStack item = inventory.getItem(i);
                if (item != null && item.getType() == Material.ARROW && item.hasItemMeta() &&
                        item.getItemMeta().getPersistentDataContainer().has(vaultPageKey, PersistentDataType.INTEGER)) {
                    continue;
                }
            }

            ItemStack item = inventory.getItem(i);
            newItems[i] = (item != null) ? item.clone() : null;

            if (!Objects.equals(currentItems[i], newItems[i])) {
                changed = true;
                plugin.getLogger().info("Item changed in slot " + i + " of vault " + vault.getId() + " page " + page);
            }
        }

        if (changed) {
            plugin.getLogger().info("Updating vault " + vault.getId() + " page " + page + " with new contents");
            vault.setPageItems(page, newItems);
            vault.markDirty();

            storageManager.saveVault(vault).thenAccept(success -> {
                plugin.getLogger().info("Saved vault " + vault.getId() + " page " + page + " to database: " + success);
                if (success) {
                    vault.markClean();
                    Player updater = null;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (inventory.getViewers().contains(p)) {
                            updater = p;
                            break;
                        }
                    }
                    if (updater != null) {
                        final UUID updaterUuid = updater.getUniqueId();
                        plugin.getVaultUpdateManager().updateNationVaultViewers(vault.getId(), page, updaterUuid,
                                inventory);
                    }
                }
            });
        } else {
            plugin.getLogger().info("No changes detected in vault " + vault.getId() + " page " + page);
        }
    }

    public void handlePlayerQuit(UUID playerId) {
        PlayerVaultSession session = playerSessions.get(playerId);
        if (session == null)
            return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.getOpenInventory() != null) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory != null && inventory.getHolder() instanceof VaultInventoryHolder) {
                VaultInventoryHolder holder = (VaultInventoryHolder) inventory.getHolder();
                String vaultId = holder.getVaultId();

                NationVault vault = null;
                for (NationVault v : nationVaults.values()) {
                    if (v.getId().equals(vaultId)) {
                        vault = v;
                        break;
                    }
                }

                if (vault != null) {
                    saveInventoryToVault(inventory, vault, holder.getPage());
                }
            }
        }

        playerSessions.remove(playerId);
        plugin.getLogger().info("Cleaned up vault session for disconnected player " + playerId);
    }

    public boolean isNavigationItem(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        return item.getItemMeta().getPersistentDataContainer().has(vaultPageKey, PersistentDataType.INTEGER);
    }

    public CompletableFuture<NationVault> getOrCreateVault(String nationId) {
        return storageManager.getOrCreateVault(nationId, nationVaults);
    }

    public CompletableFuture<Boolean> saveVault(NationVault vault) {
        return storageManager.saveVault(vault);
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
     * Check for inventory changes and save if needed
     * Called after inventory interactions to ensure state is saved
     */
    public void checkAndSaveInventoryChanges(Player player, VaultInventoryHolder holder) {
        String vaultId = holder.getVaultId();
        int page = holder.getPage();

        NationVault vault = null;
        for (NationVault v : nationVaults.values()) {
            if (v.getId().equals(vaultId)) {
                vault = v;
                break;
            }
        }

        if (vault == null) {
            plugin.getLogger().warning("[DEBUG] Could not find vault " + vaultId +
                    " when checking changes for " + player.getName());
            return;
        }

        // Get the inventory to check
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory != null && inventory.getHolder() == holder) {
            saveInventoryToVault(inventory, vault, page);
            plugin.getLogger().info("[DEBUG] Checked and saved changes for vault " + vaultId +
                    " page " + page + " by player " + player.getName());
        }
    }

    public static class VaultInventoryHolder implements InventoryHolder {
        private final String vaultId;
        private final String nationId;
        private final int page;
        private Inventory inventory;

        public VaultInventoryHolder(String vaultId, String nationId, int page) {
            this.vaultId = vaultId;
            this.nationId = nationId;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        public String getVaultId() {
            return vaultId;
        }

        public String getNationId() {
            return nationId;
        }

        public int getPage() {
            return page;
        }
    }

    private static class PlayerVaultSession {
        private final String nationId;
        private final int page;

        public PlayerVaultSession(String nationId, int page) {
            this.nationId = nationId;
            this.page = page;
        }
    }

    public static class NationVault {
        private final String id;
        private final String nationId;
        private Map<Integer, ItemStack[]> pages;
        private ItemStack[] overflowItems;
        private Date overflowExpiry;
        private boolean dirty;

        public NationVault(String id, String nationId, Map<Integer, ItemStack[]> pages,
                ItemStack[] overflowItems, Date overflowExpiry) {
            this.id = id;
            this.nationId = nationId;
            this.pages = pages != null ? new HashMap<>(pages) : new HashMap<>();
            this.overflowItems = overflowItems;
            this.overflowExpiry = overflowExpiry;
            this.dirty = false;
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
            ItemStack[] items = pages.computeIfAbsent(page, k -> new ItemStack[MAX_SINGLE_PAGE_SIZE]);

            ItemStack[] clone = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    clone[i] = items[i].clone();
                }
            }
            return clone;
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
                dirty = true;
            } else {
                pages.put(page, new ItemStack[MAX_SINGLE_PAGE_SIZE]);
                dirty = true;
            }
        }

        public boolean isDirty() {
            return dirty;
        }

        public void markDirty() {
            dirty = true;
        }

        public void markClean() {
            dirty = false;
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
}

package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages real-time updates between players viewing the same vault
 */
public class VaultUpdateManager {
    private final Sovereignty plugin;

    // Map of vault IDs to the set of players viewing that vault
    private final Map<String, Set<UUID>> nationVaultViewers = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> tradeVaultViewers = new ConcurrentHashMap<>();

    public VaultUpdateManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    /**
     * Register a player as viewing a nation vault
     * 
     * @param vaultId    The ID of the vault being viewed
     * @param playerUuid The UUID of the player viewing the vault
     * @param page       The page being viewed
     */
    public void registerNationVaultViewer(String vaultId, UUID playerUuid, int page) {
        // Create compound key including page number
        String compoundKey = vaultId + ":" + page;
        nationVaultViewers.computeIfAbsent(compoundKey, k -> new HashSet<>()).add(playerUuid);
    }

    /**
     * Register a player as viewing a trade vault
     * 
     * @param tradeId    The ID of the trade
     * @param isSender   Whether the player is viewing the sender side
     * @param playerUuid The UUID of the player viewing the vault
     */
    public void registerTradeVaultViewer(String tradeId, boolean isSender, UUID playerUuid) {
        // Create compound key including sender/receiver info
        String compoundKey = tradeId + ":" + (isSender ? "sender" : "receiver");
        tradeVaultViewers.computeIfAbsent(compoundKey, k -> new HashSet<>()).add(playerUuid);
    }

    /**
     * Unregister a player from viewing a nation vault
     * 
     * @param vaultId    The ID of the vault
     * @param playerUuid The UUID of the player
     * @param page       The page being viewed
     */
    public void unregisterNationVaultViewer(String vaultId, UUID playerUuid, int page) {
        String compoundKey = vaultId + ":" + page;
        if (nationVaultViewers.containsKey(compoundKey)) {
            nationVaultViewers.get(compoundKey).remove(playerUuid);
            if (nationVaultViewers.get(compoundKey).isEmpty()) {
                nationVaultViewers.remove(compoundKey);
            }
        }
    }

    /**
     * Unregister a player from viewing a trade vault
     * 
     * @param tradeId    The ID of the trade
     * @param isSender   Whether the player was viewing the sender side
     * @param playerUuid The UUID of the player
     */
    public void unregisterTradeVaultViewer(String tradeId, boolean isSender, UUID playerUuid) {
        String compoundKey = tradeId + ":" + (isSender ? "sender" : "receiver");
        if (tradeVaultViewers.containsKey(compoundKey)) {
            tradeVaultViewers.get(compoundKey).remove(playerUuid);
            if (tradeVaultViewers.get(compoundKey).isEmpty()) {
                tradeVaultViewers.remove(compoundKey);
            }
        }
    }

    /**
     * Update the nation vault inventory for all viewers except the specified player
     * 
     * @param vaultId          The ID of the vault
     * @param page             The page to update
     * @param updaterUuid      The UUID of the player who made the change (will be
     *                         excluded from updates)
     * @param updatedInventory The updated inventory contents
     */
    public void updateNationVaultViewers(String vaultId, int page, UUID updaterUuid, Inventory updatedInventory) {
        String compoundKey = vaultId + ":" + page;
        Set<UUID> viewers = nationVaultViewers.get(compoundKey);
        if (viewers == null) {
            plugin.getLogger().info("[DEBUG] No additional viewers found for vault " + vaultId + " page " + page);
            return;
        }

        // Create a safe copy of viewers to prevent concurrent modification
        Set<UUID> viewersCopy = new HashSet<>(viewers);
        plugin.getLogger().info(
                "[DEBUG] Found " + viewersCopy.size() + " additional viewers for vault " + vaultId + " page " + page);

        // Run on the main thread since inventory operations are not thread-safe
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                for (UUID viewerUuid : viewersCopy) {
                    // Don't update the inventory of the player who made the change
                    if (viewerUuid.equals(updaterUuid)) {
                        plugin.getLogger().info("[DEBUG] Skipping updater's inventory: " + viewerUuid);
                        continue;
                    }

                    Player viewer = Bukkit.getPlayer(viewerUuid);
                    if (viewer != null && viewer.isOnline() &&
                            viewer.getOpenInventory() != null &&
                            viewer.getOpenInventory().getTopInventory() != null &&
                            viewer.getOpenInventory().getTopInventory().getSize() == updatedInventory.getSize()) {

                        // Check if the player is still viewing the expected inventory
                        String title = viewer.getOpenInventory().title().toString();
                        if (title.startsWith("Nation Vault:") && title.contains("Page " + (page + 1))) {
                            // Update inventory contents, preserving navigation buttons
                            plugin.getLogger().info("[DEBUG] Updating inventory for viewer " + viewer.getName() +
                                    " for vault " + vaultId + " page " + page);
                            updateInventoryContentsPreservingButtons(viewer.getOpenInventory().getTopInventory(),
                                    updatedInventory, VaultService.NEXT_PAGE_SLOT, VaultService.PREV_PAGE_SLOT);
                        } else {
                            plugin.getLogger().info("[DEBUG] Viewer " + viewer.getName() +
                                    " no longer viewing the right page. Current title: " + title);
                        }
                    } else {
                        plugin.getLogger().info("[DEBUG] Viewer " + viewerUuid + " no longer valid for updates");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[DEBUG] Error updating nation vault viewers: " + e.getMessage(),
                        e);
            }
        });
    }

    /**
     * Update the trade vault inventory for all viewers except the specified player
     * 
     * @param tradeId          The ID of the trade
     * @param isSender         Whether to update the sender or receiver side
     * @param updaterUuid      The UUID of the player who made the change (will be
     *                         excluded from updates)
     * @param updatedInventory The updated inventory contents
     */
    public void updateTradeVaultViewers(String tradeId, boolean isSender, UUID updaterUuid,
            Inventory updatedInventory) {
        String compoundKey = tradeId + ":" + (isSender ? "sender" : "receiver");
        Set<UUID> viewers = tradeVaultViewers.get(compoundKey);
        if (viewers == null)
            return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (UUID viewerUuid : viewers) {
                // Don't update the inventory of the player who made the change
                if (viewerUuid.equals(updaterUuid))
                    continue;

                Player viewer = Bukkit.getPlayer(viewerUuid);
                if (viewer != null
                        && viewer.getOpenInventory().getTopInventory().getSize() == updatedInventory.getSize()) {
                    // Check if the player is still viewing the expected inventory
                    String title = viewer.getOpenInventory().title().toString();
                    String expectedTitle = (isSender ? "Trade Sending Vault" : "Trade Receiving Vault");

                    if (title.startsWith(expectedTitle)) {
                        // Update inventory contents, preserving special buttons
                        updateInventoryContentsPreservingButtons(viewer.getOpenInventory().getTopInventory(),
                                updatedInventory,
                                com.tatayless.sovereignty.services.trade.TradeVaultHandler.CONFIRM_BUTTON_SLOT,
                                com.tatayless.sovereignty.services.trade.TradeVaultHandler.INFO_BUTTON_SLOT);
                    }
                }
            }
        });
    }

    /**
     * Updates inventory contents while preserving buttons at specific slots
     */
    private void updateInventoryContentsPreservingButtons(Inventory targetInventory, Inventory sourceInventory,
            int... preserveSlots) {
        try {
            int updatedItems = 0;
            int preservedButtons = 0;
            int size = Math.min(targetInventory.getSize(), sourceInventory.getSize()); // Use the smaller size

            plugin.getLogger()
                    .info("[DEBUG] Updating viewer inventory (" + targetInventory.getType() + " size "
                            + targetInventory.getSize() + ") from source (" + sourceInventory.getType() + " size "
                            + sourceInventory.getSize() + ")");

            for (int i = 0; i < size; i++) {
                boolean isPreservedSlot = false;
                for (int slot : preserveSlots) {
                    if (i == slot) {
                        isPreservedSlot = true;
                        break;
                    }
                }

                if (isPreservedSlot) {
                    // Optionally verify the item in the target slot *is* a button before preserving
                    // This prevents preserving non-button items if the slot index is wrong
                    ItemStack targetItem = targetInventory.getItem(i);
                    // We assume the target already has the correct button, so we don't touch it.
                    // If we wanted to ensure the *correct* button is there, we'd need access to
                    // VaultService.createNavigationItem
                    if (targetItem != null) { // Check if something is actually there to preserve
                        preservedButtons++;
                        plugin.getLogger().log(Level.INFO,
                                "[DEBUG] Preserving item in slot " + i + ": " + targetItem.getType());
                    } else {
                        plugin.getLogger().log(Level.INFO,
                                "[DEBUG] Slot " + i + " marked for preservation, but is empty in target inventory.");
                        // If the source has the button, maybe place it? Requires VaultService access.
                        // For now, just leave it empty if it was empty.
                    }
                } else {
                    // Update non-preserved slots
                    ItemStack sourceItem = sourceInventory.getItem(i);
                    targetInventory.setItem(i, sourceItem != null ? sourceItem.clone() : null); // Clone for safety
                    if (sourceItem != null) {
                        updatedItems++;
                    }
                }
            }

            plugin.getLogger().info("[DEBUG] Viewer inventory update complete: " + updatedItems +
                    " items updated, " + preservedButtons + " slots preserved.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DEBUG] Error updating viewer inventory contents: " + e.getMessage(),
                    e);
        }
    }

    /**
     * Unregister a player from all vaults they might be viewing
     * 
     * @param playerUuid The UUID of the player
     */
    public void unregisterPlayerFromAllVaults(UUID playerUuid) {
        // Remove from nation vault viewers
        for (Map.Entry<String, Set<UUID>> entry : nationVaultViewers.entrySet()) {
            entry.getValue().remove(playerUuid);
            if (entry.getValue().isEmpty()) {
                nationVaultViewers.remove(entry.getKey());
            }
        }

        // Remove from trade vault viewers
        for (Map.Entry<String, Set<UUID>> entry : tradeVaultViewers.entrySet()) {
            entry.getValue().remove(playerUuid);
            if (entry.getValue().isEmpty()) {
                tradeVaultViewers.remove(entry.getKey());
            }
        }
    }
}

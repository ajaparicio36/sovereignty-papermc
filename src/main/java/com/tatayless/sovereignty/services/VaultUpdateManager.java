package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        if (viewers == null)
            return;

        // Run on the main thread since inventory operations are not thread-safe
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
                    if (title.startsWith("Nation Vault:") && title.contains("Page " + (page + 1))) {
                        // Update inventory contents, preserving navigation buttons
                        updateInventoryContentsPreservingButtons(viewer.getOpenInventory().getTopInventory(),
                                updatedInventory, VaultService.NEXT_PAGE_SLOT, VaultService.PREV_PAGE_SLOT);
                    }
                }
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
        for (int i = 0; i < targetInventory.getSize() && i < sourceInventory.getSize(); i++) {
            boolean isPreservedSlot = false;
            for (int slot : preserveSlots) {
                if (i == slot) {
                    isPreservedSlot = true;
                    break;
                }
            }

            if (!isPreservedSlot) {
                targetInventory.setItem(i, sourceInventory.getItem(i));
            }
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

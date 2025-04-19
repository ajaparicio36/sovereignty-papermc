package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.services.VaultService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;
import java.util.logging.Level;

public class VaultListener implements Listener {
    private final Sovereignty plugin;
    private final VaultService vaultService;

    public VaultListener(Sovereignty plugin, VaultService vaultService) {
        this.plugin = plugin;
        this.vaultService = vaultService;
        plugin.getLogger().info("VaultListener initialized with VaultService reference.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        Player player = (Player) event.getWhoClicked();
        Inventory topInventory = event.getView().getTopInventory();
        String title = event.getView().title().toString();

        if (!title.startsWith("Nation Vault:"))
            return;

        plugin.getLogger().log(Level.INFO, "[DEBUG] Vault click by " + player.getName() +
                " - RawSlot: " + event.getRawSlot() +
                " Slot: " + event.getSlot() +
                " ClickType: " + event.getClick() +
                " Action: " + event.getAction());

        int rawSlot = event.getRawSlot();
        boolean clickedTopInventory = event.getClickedInventory() == topInventory;

        if (isNavigationSlot(rawSlot) && clickedTopInventory) {
            plugin.getLogger().log(Level.INFO, "[DEBUG] Click detected in navigation slot: " + rawSlot);
            event.setCancelled(true);

            if (event.getClick() == ClickType.LEFT) {
                if (rawSlot == VaultService.NEXT_PAGE_SLOT) {
                    plugin.getLogger().info("[DEBUG] Processing navigation to NEXT page");
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getLogger().info("[DEBUG] Executing next page navigation task");
                        vaultService.navigateToNextPage(player);
                    }, 1L); // Short delay for stability
                } else if (rawSlot == VaultService.PREV_PAGE_SLOT) {
                    plugin.getLogger().info("[DEBUG] Processing navigation to PREVIOUS page");
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getLogger().info("[DEBUG] Executing previous page navigation task");
                        vaultService.navigateToPreviousPage(player);
                    }, 1L); // Short delay for stability
                }
            }
            return;
        }

        if (!event.isCancelled()) {
            plugin.getLogger().info("[DEBUG] Regular click in vault UI, scheduling update");
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getLogger().info("[DEBUG] Executing scheduled vault update after click");
                vaultService.updateAndSaveVaultForPlayer(player);
            }, 1L); // Short delay for stability
        }
    }

    private boolean isNavigationSlot(int slot) {
        return slot == VaultService.NEXT_PAGE_SLOT || slot == VaultService.PREV_PAGE_SLOT;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        String title = event.getView().title().toString();
        if (!title.startsWith("Nation Vault:"))
            return;

        Player player = (Player) event.getWhoClicked();
        plugin.getLogger().log(Level.INFO, "[DEBUG] Vault drag by " + player.getName() +
                " - Slots: " + event.getRawSlots() +
                " NewItems: " + event.getNewItems());

        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                if (isNavigationSlot(slot)) {
                    plugin.getLogger().info("[DEBUG] Cancelled drag involving navigation slot: " + slot);
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (!event.isCancelled()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory().title().toString().startsWith("Nation Vault:")) {
                    plugin.getLogger()
                            .info("[DEBUG] Running scheduled vault update after drag for player " + player.getName());
                    vaultService.updateAndSaveVaultForPlayer(player);
                } else {
                    plugin.getLogger().info("[DEBUG] Skipped scheduled vault update for " + player.getName()
                            + " (offline or different inventory after drag).");
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        String title = event.getView().title().toString();
        if (!title.startsWith("Nation Vault:"))
            return;

        Player player = (Player) event.getPlayer();
        plugin.getLogger().info("[DEBUG] Inventory close event for vault by " + player.getName());
        vaultService.handleInventoryClose(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        plugin.getLogger().info("[DEBUG] Player quit event for " + event.getPlayer().getName());
        vaultService.handlePlayerQuit(playerUuid);
        plugin.getVaultUpdateManager().unregisterPlayerFromAllVaults(playerUuid);
    }
}

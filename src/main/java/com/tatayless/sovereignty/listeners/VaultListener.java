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
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.logging.Level;

public class VaultListener implements Listener {
    private final Sovereignty plugin;
    private final VaultService vaultService;

    public VaultListener(Sovereignty plugin, VaultService vaultService) {
        this.plugin = plugin;
        this.vaultService = vaultService;
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
                " Action: " + event.getAction() +
                " CurrentItem: " + event.getCurrentItem() +
                " Cursor: " + event.getCursor() +
                " ClickedInventoryType: "
                + (event.getClickedInventory() != null ? event.getClickedInventory().getType() : "NULL"));

        int rawSlot = event.getRawSlot();
        boolean clickedTopInventory = event.getClickedInventory() == topInventory;

        if (isNavigationSlot(rawSlot) && clickedTopInventory) {
            plugin.getLogger().log(Level.INFO, "[DEBUG] Click detected in navigation slot: " + rawSlot);
            event.setCancelled(true);

            if (event.getClick() == ClickType.LEFT) {
                if (rawSlot == VaultService.NEXT_PAGE_SLOT) {
                    plugin.getLogger().info("[DEBUG] Processing navigation to NEXT page for " + player.getName());
                    plugin.getServer().getScheduler().runTask(plugin, () -> vaultService.navigateToNextPage(player));
                } else if (rawSlot == VaultService.PREV_PAGE_SLOT) {
                    plugin.getLogger().info("[DEBUG] Processing navigation to PREVIOUS page for " + player.getName());
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> vaultService.navigateToPreviousPage(player));
                }
            }
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarSlot = event.getHotbarButton();
            ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
            if (isNavigationSlot(event.getSlot()) && clickedTopInventory) {
                plugin.getLogger().log(Level.INFO,
                        "[DEBUG] Cancelled NUMBER_KEY swap into navigation slot " + event.getSlot());
                event.setCancelled(true);
                return;
            }
            if (isNavigationSlot(event.getSlot()) && clickedTopInventory
                    && vaultService.isNavigationButton(event.getCurrentItem())) {
                plugin.getLogger().log(Level.INFO,
                        "[DEBUG] Cancelled NUMBER_KEY swap from navigation slot " + event.getSlot());
                event.setCancelled(true);
                return;
            }
        }

        if (event.isShiftClick()) {
            if (!clickedTopInventory) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null) {
                    int targetSlot = findShiftClickTargetSlot(topInventory, clickedItem);
                    if (isNavigationSlot(targetSlot)) {
                        plugin.getLogger().log(Level.INFO,
                                "[DEBUG] Cancelled shift-click targeting navigation slot " + targetSlot);
                        event.setCancelled(true);
                        return;
                    }
                }
            } else {
                if (isNavigationSlot(event.getSlot()) && vaultService.isNavigationButton(event.getCurrentItem())) {
                    plugin.getLogger().log(Level.INFO,
                            "[DEBUG] Cancelled shift-click of navigation button out of slot " + event.getSlot());
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (clickedTopInventory && isNavigationSlot(event.getSlot())
                && vaultService.isNavigationButton(event.getCurrentItem())) {
            plugin.getLogger().log(Level.INFO,
                    "[DEBUG] Cancelled pickup of navigation button from slot " + event.getSlot());
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            if (clickedTopInventory && isNavigationSlot(event.getSlot()) && !event.getCursor().getType().isAir()) {
                plugin.getLogger().log(Level.INFO, "[DEBUG] Cancelled drop onto navigation slot " + event.getSlot());
                event.setCancelled(true);
                return;
            }
        }

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            if (isNavigationSlot(event.getSlot()) && clickedTopInventory) {
                plugin.getLogger().log(Level.INFO,
                        "[DEBUG] Cancelled double-click involving navigation slot " + event.getSlot());
                event.setCancelled(true);
                return;
            }
        }

        if (event.isCancelled()) {
            plugin.getLogger().log(Level.INFO,
                    "[DEBUG] Vault click event cancelled for " + player.getName() + ". No update scheduled.");
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().title().toString().startsWith("Nation Vault:")) {
                plugin.getLogger()
                        .info("[DEBUG] Running scheduled vault update after click for player " + player.getName());
                vaultService.updateAndSaveVaultForPlayer(player);
            } else {
                plugin.getLogger().info("[DEBUG] Skipped scheduled vault update for " + player.getName()
                        + " (offline or different inventory).");
            }
        });
    }

    private boolean isNavigationSlot(int slot) {
        return slot == VaultService.NEXT_PAGE_SLOT || slot == VaultService.PREV_PAGE_SLOT;
    }

    private int findShiftClickTargetSlot(Inventory topInventory, ItemStack clickedItem) {
        int firstPartial = -1;
        int firstEmpty = -1;

        for (int i = 0; i < topInventory.getSize(); i++) {
            if (isNavigationSlot(i))
                continue;

            ItemStack current = topInventory.getItem(i);
            if (current != null && current.isSimilar(clickedItem) && current.getAmount() < current.getMaxStackSize()) {
                if (firstPartial == -1) {
                    firstPartial = i;
                }
            } else if (current == null || current.getType().isAir()) {
                if (firstEmpty == -1) {
                    firstEmpty = i;
                }
            }
        }

        if (firstPartial != -1)
            return firstPartial;
        if (firstEmpty != -1)
            return firstEmpty;

        return -1;
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

        if (event.isCancelled()) {
            plugin.getLogger().log(Level.INFO,
                    "[DEBUG] Vault drag event cancelled for " + player.getName() + ". No update scheduled.");
            return;
        }

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
        vaultService.handlePlayerQuit(playerUuid);
        plugin.getVaultUpdateManager().unregisterPlayerFromAllVaults(playerUuid);
        plugin.getLogger().info("[DEBUG] Cleaned up vault session for quitting player " + event.getPlayer().getName());
    }
}

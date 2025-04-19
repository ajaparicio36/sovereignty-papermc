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

import java.util.logging.Level;

public class VaultListener implements Listener {
    private final Sovereignty plugin;
    private final VaultService vaultService;

    public VaultListener(Sovereignty plugin, VaultService vaultService) {
        this.plugin = plugin;
        this.vaultService = vaultService;
        plugin.getLogger().info("VaultListener initialized with VaultService reference.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        // Check if this is a vault inventory
        if (!(event.getInventory().getHolder() instanceof VaultService.VaultInventoryHolder))
            return;

        Player player = (Player) event.getWhoClicked();

        // Debug the click
        plugin.getLogger().log(Level.INFO, "Vault click: Player=" + player.getName() +
                ", Slot=" + event.getSlot() +
                ", RawSlot=" + event.getRawSlot() +
                ", Action=" + event.getAction() +
                ", ClickType=" + event.getClick());

        // Check for navigation button clicks
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && vaultService.isNavigationItem(clickedItem)) {
            plugin.getLogger().info("Navigation button clicked by " + player.getName());
            event.setCancelled(true);

            // Schedule navigation
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                vaultService.handleNavigationClick(player, clickedItem);
            });
            return;
        }

        // Cancel any action that would put items in navigation slots
        VaultService.VaultInventoryHolder holder = (VaultService.VaultInventoryHolder) event.getInventory().getHolder();
        if (holder != null && event.getInventory().getSize() == 54) {
            int rawSlot = event.getRawSlot();
            // If this is a SHIFT_CLICK from player inventory (bottom) to vault inventory
            // (top)
            if (event.getClick().isShiftClick() && rawSlot >= 54) {
                // Cancel the event if the destination slot would be a navigation button
                if (wouldShiftClickToNavigationSlot(event)) {
                    plugin.getLogger().info("Cancelled shift-click that would affect navigation slots");
                    event.setCancelled(true);
                    return;
                }
            }
            // For direct clicks on navigation slots
            else if ((rawSlot == VaultService.NEXT_PAGE_SLOT || rawSlot == VaultService.PREV_PAGE_SLOT) &&
                    rawSlot < 54) {
                plugin.getLogger().info("Cancelled click on navigation slot " + rawSlot);
                event.setCancelled(true);
                return;
            }
            // For swaps with hotbar
            else if (event.getClick() == ClickType.NUMBER_KEY) {
                int slot = event.getSlot();
                if (slot == VaultService.NEXT_PAGE_SLOT || slot == VaultService.PREV_PAGE_SLOT) {
                    plugin.getLogger().info("Cancelled hotbar swap with navigation slot " + slot);
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean wouldShiftClickToNavigationSlot(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null)
            return false;

        Inventory topInventory = event.getView().getTopInventory();
        // Find first valid slot for this item
        for (int i = 0; i < topInventory.getSize(); i++) {
            // Skip navigation slots
            if (i == VaultService.NEXT_PAGE_SLOT || i == VaultService.PREV_PAGE_SLOT) {
                continue;
            }

            ItemStack current = topInventory.getItem(i);
            if (current == null || current.getType().isAir()) {
                return false; // Found a valid slot, won't go to navigation
            }

            // Check for partial stack
            if (current.isSimilar(clickedItem) && current.getAmount() < current.getMaxStackSize()) {
                return false; // Found a partial stack, won't go to navigation
            }
        }

        // No other slots available, might go to navigation slot
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        // Check if this is a vault inventory
        if (!(event.getInventory().getHolder() instanceof VaultService.VaultInventoryHolder))
            return;

        // Cancel if drag affects navigation buttons
        if (event.getInventory().getSize() == 54) {
            for (int slot : event.getRawSlots()) {
                if ((slot == VaultService.NEXT_PAGE_SLOT || slot == VaultService.PREV_PAGE_SLOT) && slot < 54) {
                    plugin.getLogger().info("Cancelled drag affecting navigation slots");
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;
        if (!(event.getInventory().getHolder() instanceof VaultService.VaultInventoryHolder))
            return;

        plugin.getLogger().info("Player " + event.getPlayer().getName() + " closing vault inventory");
        vaultService.handleInventoryClose(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getLogger().info("Player quit event: " + event.getPlayer().getName());
        vaultService.handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}

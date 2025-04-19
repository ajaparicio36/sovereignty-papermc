package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.services.TradeService;
import com.tatayless.sovereignty.services.trade.TradeVaultHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

public class TradeListener implements Listener {
    private final Sovereignty plugin;
    private final TradeService tradeService;

    public TradeListener(Sovereignty plugin, TradeService tradeService) {
        this.plugin = plugin;
        this.tradeService = tradeService;
        plugin.getLogger().info("TradeListener initialized with TradeService reference.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        // Handle trade vault clicks specially
        if (event.getInventory().getHolder() instanceof TradeVaultHandler.TradeVaultInventoryHolder) {
            Player player = (Player) event.getWhoClicked();

            // Debug info
            plugin.getLogger().log(Level.INFO, "Trade vault click: Player=" + player.getName() +
                    ", Slot=" + event.getSlot() +
                    ", RawSlot=" + event.getRawSlot() +
                    ", Action=" + event.getAction());

            // Let the TradeService handle it
            tradeService.handleInventoryClick(event);
            return;
        }

        // Handle other trade menu clicks if player has a trade session
        if (event.getView().title().toString().toLowerCase().contains("trade")) {
            tradeService.handleInventoryClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        // Check if this is a trade vault inventory
        if (!(event.getInventory().getHolder() instanceof TradeVaultHandler.TradeVaultInventoryHolder))
            return;

        @SuppressWarnings("unused")
        TradeVaultHandler.TradeVaultInventoryHolder holder = (TradeVaultHandler.TradeVaultInventoryHolder) event
                .getInventory().getHolder();

        // Cancel if drag affects special buttons
        for (int slot : event.getRawSlots()) {
            if ((slot == TradeVaultHandler.CONFIRM_BUTTON_SLOT ||
                    slot == TradeVaultHandler.INFO_BUTTON_SLOT) && slot < 54) {
                plugin.getLogger().info("Cancelled drag affecting trade vault buttons");
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        // Handle trade vault inventories
        if (event.getInventory().getHolder() instanceof TradeVaultHandler.TradeVaultInventoryHolder) {
            plugin.getLogger().info("Player " + event.getPlayer().getName() + " closing trade vault inventory");
            tradeService.handleInventoryClose(event);
            return;
        }

        // Handle other trade-related inventories if the title contains "trade"
        if (event.getView().title().toString().toLowerCase().contains("trade")) {
            tradeService.handleInventoryClose(event);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getLogger().info("Player quit event for trades: " + event.getPlayer().getName());

        // If player has an open trade vault inventory, save it before they disconnect
        if (event.getPlayer().getOpenInventory() != null &&
                event.getPlayer().getOpenInventory().getTopInventory()
                        .getHolder() instanceof TradeVaultHandler.TradeVaultInventoryHolder) {

            Player player = event.getPlayer();
            tradeService.handleInventoryClose(
                    new InventoryCloseEvent(player.getOpenInventory()));
        }

        // Remove player from trade sessions
        tradeService.getPlayerSessions().remove(event.getPlayer().getUniqueId());
    }
}

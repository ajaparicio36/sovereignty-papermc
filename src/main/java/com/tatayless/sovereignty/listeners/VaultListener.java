package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.services.VaultService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class VaultListener implements Listener {
    private final Sovereignty plugin;
    private final VaultService vaultService;

    public VaultListener(Sovereignty plugin, VaultService vaultService) {
        this.plugin = plugin;
        this.vaultService = vaultService;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        String title = event.getView().title().toString();
        if (!title.startsWith("Nation Vault:"))
            return;

        vaultService.handleInventoryClick(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;

        String title = event.getView().title().toString();
        if (!title.startsWith("Nation Vault:"))
            return;

        // After drag, make sure to save the changes
        Player player = (Player) event.getWhoClicked();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            vaultService.updateAndSaveVaultForPlayer(player);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player))
            return;

        String title = event.getView().title().toString();
        if (!title.startsWith("Nation Vault:"))
            return;

        vaultService.handleInventoryClose(event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        vaultService.cleanupPlayerSession(playerUuid);
        plugin.getVaultUpdateManager().unregisterPlayerFromAllVaults(playerUuid);
    }
}

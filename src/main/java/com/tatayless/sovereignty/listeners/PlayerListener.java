package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    private final Sovereignty plugin;
    private final PlayerService playerService;

    public PlayerListener(Sovereignty plugin) {
        this.plugin = plugin;
        this.playerService = plugin.getServiceManager().getPlayerService();
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String playerId = player.getUniqueId().toString();

        // Check if player exists in our system
        SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(playerId);

        if (sovereigntyPlayer == null) {
            // Create new player
            sovereigntyPlayer = playerService.createPlayer(player);
            plugin.getLogger().info("Created new player profile for " + player.getName());
        } else if (!sovereigntyPlayer.getName().equals(player.getName())) {
            // Update player name if changed
            sovereigntyPlayer.setName(player.getName());
            playerService.updatePlayer(sovereigntyPlayer);
            plugin.getLogger().info("Updated name for player " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nothing special needed here for now, but could be used for cleanup tasks
    }
}

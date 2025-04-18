package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.PlayerService;
import com.tatayless.sovereignty.services.WarService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class WarListener implements Listener {
    @SuppressWarnings("unused")
    private final Sovereignty plugin;
    private final PlayerService playerService;
    private final WarService warService;

    public WarListener(Sovereignty plugin) {
        this.plugin = plugin;
        this.playerService = plugin.getServiceManager().getPlayerService();
        this.warService = plugin.getServiceManager().getWarService();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Only process if killed by another player
        if (killer == null)
            return;

        String victimId = victim.getUniqueId().toString();
        String killerId = killer.getUniqueId().toString();

        // Get player data
        SovereigntyPlayer victimPlayer = playerService.getPlayer(victimId);
        SovereigntyPlayer killerPlayer = playerService.getPlayer(killerId);

        if (victimPlayer == null || killerPlayer == null ||
                !victimPlayer.hasNation() || !killerPlayer.hasNation()) {
            return;
        }

        // Check if nations are at war
        if (!warService.isAtWar(killerPlayer.getNationId(), victimPlayer.getNationId())) {
            return;
        }

        // Record the kill for war
        warService.recordKill(killerId, victimId).thenAccept(success -> {
            if (success) {
                // Death message is handled by the war service
            }
        });
    }
}

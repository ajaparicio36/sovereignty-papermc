package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.ChunkLocation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.NationService;
import com.tatayless.sovereignty.services.PlayerService;
import com.tatayless.sovereignty.services.WarService;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

@SuppressWarnings("unused")
public class ProtectionListener implements Listener {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final PlayerService playerService;
    private final WarService warService;

    public ProtectionListener(Sovereignty plugin) {
        this.plugin = plugin;
        this.nationService = plugin.getServiceManager().getNationService();
        this.playerService = plugin.getServiceManager().getPlayerService();
        this.warService = plugin.getServiceManager().getWarService();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if the block is in a claimed chunk
        ChunkLocation chunkLocation = new ChunkLocation(block.getChunk());
        Nation ownerNation = nationService.getNationByChunk(chunkLocation);

        if (ownerNation != null) {
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(player.getUniqueId().toString());

            // Check if player has permission to break blocks in this nation
            if (sovereigntyPlayer == null ||
                    (!ownerNation.isMember(sovereigntyPlayer.getId()) &&
                            !player.hasPermission("sovereignty.admin.bypass"))) {

                // Check if player's nation is at war with owner nation and war destruction is
                // enabled
                boolean warException = false;
                if (sovereigntyPlayer != null && sovereigntyPlayer.hasNation()) {
                    Nation playerNation = nationService.getNation(sovereigntyPlayer.getNationId());
                    if (playerNation != null &&
                            warService.isAtWar(playerNation.getId(), ownerNation.getId()) &&
                            plugin.getConfigManager().isWarDestructionEnabled()) {
                        warException = true;
                    }
                }

                if (!warException) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getLocalizationManager().getMessage(
                            "protection.cannot-break",
                            "nation", ownerNation.getName()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if the block is in a claimed chunk
        ChunkLocation chunkLocation = new ChunkLocation(block.getChunk());
        Nation ownerNation = nationService.getNationByChunk(chunkLocation);

        if (ownerNation != null) {
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(player.getUniqueId().toString());

            // Check if player has permission to place blocks in this nation
            if (sovereigntyPlayer == null ||
                    (!ownerNation.isMember(sovereigntyPlayer.getId()) &&
                            !player.hasPermission("sovereignty.admin.bypass"))) {

                // Check if player's nation is at war with owner nation and war destruction is
                // enabled
                boolean warException = false;
                if (sovereigntyPlayer != null && sovereigntyPlayer.hasNation()) {
                    Nation playerNation = nationService.getNation(sovereigntyPlayer.getNationId());
                    if (playerNation != null &&
                            warService.isAtWar(playerNation.getId(), ownerNation.getId()) &&
                            plugin.getConfigManager().isWarDestructionEnabled()) {
                        warException = true;
                    }
                }

                if (!warException) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getLocalizationManager().getMessage(
                            "protection.cannot-place",
                            "nation", ownerNation.getName()));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null)
            return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        // Check if the block is in a claimed chunk
        ChunkLocation chunkLocation = new ChunkLocation(block.getChunk());
        Nation ownerNation = nationService.getNationByChunk(chunkLocation);

        if (ownerNation != null) {
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(player.getUniqueId().toString());

            // Check if player has permission to interact with blocks in this nation
            if (sovereigntyPlayer == null ||
                    (!ownerNation.isMember(sovereigntyPlayer.getId()) &&
                            !player.hasPermission("sovereignty.admin.bypass"))) {

                event.setCancelled(true);
                player.sendMessage(plugin.getLocalizationManager().getMessage(
                        "protection.cannot-interact",
                        "nation", ownerNation.getName()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player))
            return;

        Player player = (Player) event.getDamager();
        Entity target = event.getEntity();

        // Check if the entity is in a claimed chunk
        ChunkLocation chunkLocation = new ChunkLocation(target.getLocation().getChunk());
        Nation ownerNation = nationService.getNationByChunk(chunkLocation);

        if (ownerNation != null) {
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(player.getUniqueId().toString());

            // Check if player has permission to damage entities in this nation
            if (sovereigntyPlayer == null ||
                    (!ownerNation.isMember(sovereigntyPlayer.getId()) &&
                            !player.hasPermission("sovereignty.admin.bypass"))) {

                // If target is a player, check for assassination mode and war
                if (target instanceof Player) {
                    Player targetPlayer = (Player) target;
                    SovereigntyPlayer targetSovPlayer = playerService.getPlayer(targetPlayer.getUniqueId().toString());

                    // Allow PvP for war
                    if (sovereigntyPlayer != null && sovereigntyPlayer.hasNation() &&
                            targetSovPlayer != null && targetSovPlayer.hasNation()) {
                        Nation attackerNation = nationService.getNation(sovereigntyPlayer.getNationId());
                        Nation defenderNation = nationService.getNation(targetSovPlayer.getNationId());

                        if (attackerNation != null && defenderNation != null &&
                                warService.isAtWar(attackerNation.getId(), defenderNation.getId())) {
                            return; // Allow the damage (war PvP)
                        }

                        // Check assassination mode
                        if (plugin.getConfigManager().isAssassinationModeEnabled() &&
                                targetSovPlayer.isPresident()) {
                            return; // Allow the damage (assassination attempt)
                        }
                    }
                }

                event.setCancelled(true);
                player.sendMessage(plugin.getLocalizationManager().getMessage(
                        "protection.cannot-damage-entity",
                        "nation", ownerNation.getName()));
            }
        }
    }
}

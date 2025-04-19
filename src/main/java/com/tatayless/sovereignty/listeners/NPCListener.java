package com.tatayless.sovereignty.listeners;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.NationService;
import com.tatayless.sovereignty.services.PlayerService;
import com.tatayless.sovereignty.services.TradeService;
import com.tatayless.sovereignty.services.VaultService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCListener implements Listener {
    private final Sovereignty plugin;
    private final PlayerService playerService;
    private final NationService nationService;
    private final VaultService vaultService;
    private final TradeService tradeService;

    public NPCListener(Sovereignty plugin) {
        this.plugin = plugin;
        this.playerService = plugin.getServiceManager().getPlayerService();
        this.nationService = plugin.getServiceManager().getNationService();
        this.vaultService = plugin.getServiceManager().getVaultService();
        this.tradeService = plugin.getServiceManager().getTradeService();
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // Only handle interactions with villagers (NPCs)
        if (!(entity instanceof Villager)) {
            return;
        }

        int entityId = entity.getEntityId();
        String vaultId = vaultService.getVaultIdFromEntity(entityId);

        // If this is a nation vault NPC
        if (vaultId != null) {
            event.setCancelled(true); // Cancel normal villager interaction

            String playerId = player.getUniqueId().toString();
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(playerId);

            if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
                return;
            }

            // Open the corresponding nation vault
            String nationId = sovereigntyPlayer.getNationId();
            Nation nation = nationService.getNation(nationId);

            if (nation == null) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("vault.no-vault"));
                return;
            }

            player.sendMessage(plugin.getLocalizationManager().getComponent("vault.opened"));
            vaultService.openVault(player, nationId);
            return;
        }

        // Check if this is a trade vault NPC
        String tradeId = tradeService.getTradeIdFromEntity(entityId);
        if (tradeId != null) {
            event.setCancelled(true); // Cancel normal villager interaction

            String playerId = player.getUniqueId().toString();
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(playerId);

            if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
                return;
            }

            // Determine if this NPC is for the sending or receiving nation
            boolean isSenderNPC = tradeService.isSenderEntity(entityId);
            String nationId = sovereigntyPlayer.getNationId();

            // Open the corresponding trade vault
            tradeService.openTradeVault(player, nationId, tradeId);
        }
    }

    /**
     * Prevent any damage to Vault or Trade NPCs
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) {
            return;
        }

        int entityId = entity.getEntityId();
        String vaultId = vaultService.getVaultIdFromEntity(entityId);
        String tradeId = tradeService.getTradeIdFromEntity(entityId);

        // If this is a vault NPC or trade NPC, cancel all damage
        if (vaultId != null || tradeId != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent entities from targeting Vault or Trade NPCs
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() == null) {
            return;
        }

        Entity target = event.getTarget();
        if (!(target instanceof Villager)) {
            return;
        }

        int entityId = target.getEntityId();
        String vaultId = vaultService.getVaultIdFromEntity(entityId);
        String tradeId = tradeService.getTradeIdFromEntity(entityId);

        // If this is a vault NPC or trade NPC, prevent targeting
        if (vaultId != null || tradeId != null) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent entities from damaging Vault or Trade NPCs
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) {
            return;
        }

        int entityId = entity.getEntityId();
        String vaultId = vaultService.getVaultIdFromEntity(entityId);
        String tradeId = tradeService.getTradeIdFromEntity(entityId);

        // If this is a vault NPC or trade NPC, cancel all damage
        if (vaultId != null || tradeId != null) {
            event.setCancelled(true);
            // If a player is attacking, let them know it's protected
            if (event.getDamager() instanceof Player) {
                Player player = (Player) event.getDamager();
                player.sendMessage(plugin.getLocalizationManager().getComponent("npc.protected"));
            }
        }
    }
}

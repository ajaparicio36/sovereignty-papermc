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
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class NPCListener implements Listener {
    private final Sovereignty plugin;
    private final PlayerService playerService;
    private final NationService nationService;
    private final VaultService vaultService;
    @SuppressWarnings("unused")
    private final TradeService tradeService;

    public NPCListener(Sovereignty plugin) {
        this.plugin = plugin;
        this.playerService = plugin.getServiceManager().getPlayerService();
        this.nationService = plugin.getServiceManager().getNationService();
        this.vaultService = plugin.getServiceManager().getVaultService();
        this.tradeService = plugin.getServiceManager().getTradeService();
    }

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
        // This would require additional lookup similar to the vault NPC check
        // For brevity, assuming we have another mapping in VaultService for trade NPCs

        // If this is a trade vault NPC, handle accordingly
        // This would look up the trade ID and whether this is for sending or receiving
        // items
        // Then open the appropriate trade vault interface
    }
}

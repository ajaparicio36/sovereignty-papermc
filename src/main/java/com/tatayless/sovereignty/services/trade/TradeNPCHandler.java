package com.tatayless.sovereignty.services.trade;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TradeNPCHandler {
    private final TradeService tradeService;
    private final Sovereignty plugin;

    public TradeNPCHandler(TradeService tradeService, Sovereignty plugin) {
        this.tradeService = tradeService;
        this.plugin = plugin;
    }

    public void openTradeNPCSelectionMenu(Player player, String nationId) {
        // Get trades where this nation is involved
        List<Trade> nationTrades = tradeService.getActiveTrades().values().stream()
                .filter(t -> t.getSendingNationId().equals(nationId) || t.getReceivingNationId().equals(nationId))
                .filter(t -> t.getStatus() == Trade.Status.PENDING || t.getStatus() == Trade.Status.ACTIVE)
                .collect(Collectors.toList());

        if (nationTrades.isEmpty()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.no-trades"));
            return;
        }

        // Create inventory for trade selection
        int rows = (int) Math.ceil(nationTrades.size() / 9.0) + 1; // +1 for margin
        rows = Math.min(6, Math.max(1, rows)); // Between 1 and 6 rows

        Inventory inventory = Bukkit.createInventory(null, rows * 9,
                net.kyori.adventure.text.Component.text("Create Trade NPC"));

        // Populate with trade items
        for (int i = 0; i < nationTrades.size() && i < inventory.getSize(); i++) {
            Trade trade = nationTrades.get(i);
            ItemStack tradeItem = TradeItemsUtil.createTradeItem(tradeService, trade);
            inventory.setItem(i, tradeItem);
        }

        // Open the inventory
        player.openInventory(inventory);

        // Create a session for NPC creation
        tradeService.getPlayerSessions().put(player.getUniqueId(),
                new TradeSession(TradeSessionType.NPC_CREATE, nationId, null, 0));
    }

    public void handleTradeNPCCreateClick(InventoryClickEvent event, TradeSession session) {
        if (event.getCurrentItem() == null)
            return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        // Get trade ID from item
        ItemStack item = event.getCurrentItem();
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore())
            return;

        String tradeId = TradeItemsUtil.extractTradeIdFromLore(item.getItemMeta().lore());
        if (tradeId == null)
            return;

        // Check if this nation is the sender or receiver
        Trade trade = tradeService.getActiveTrades().get(tradeId);
        if (trade == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.not-found"));
            return;
        }

        boolean isSender = trade.getSendingNationId().equals(session.getNationId());

        // Create the NPC
        createTradeNPC(session.getNationId(), tradeId, player.getLocation(), isSender)
                .thenAccept(success -> {
                    if (success) {
                        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-created"));
                    } else {
                        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-creation-failed"));
                    }
                });

        player.closeInventory();
    }

    public CompletableFuture<Boolean> createTradeNPC(String nationId, String tradeId, Location location,
            boolean isSender) {
        Trade trade = tradeService.getActiveTrades().get(tradeId);
        if (trade == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Verify this nation is part of the trade
        if (!trade.getSendingNationId().equals(nationId) && !trade.getReceivingNationId().equals(nationId)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    Nation nation = tradeService.getNationService().getNation(nationId);
                    if (nation == null)
                        return false;

                    final String partnerNationName;
                    if (isSender) {
                        Nation receivingNation = tradeService.getNationService()
                                .getNation(trade.getReceivingNationId());
                        partnerNationName = receivingNation != null ? receivingNation.getName() : "Unknown";
                    } else {
                        Nation sendingNation = tradeService.getNationService().getNation(trade.getSendingNationId());
                        partnerNationName = sendingNation != null ? sendingNation.getName() : "Unknown";
                    }

                    // Create the NPC entity
                    final Villager[] npc = { null };
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        npc[0] = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);

                        // Create stylized name for the trade NPC
                        net.kyori.adventure.text.Component nameComponent = net.kyori.adventure.text.Component
                                .text("Trade with ")
                                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                                .append(net.kyori.adventure.text.Component
                                        .text(partnerNationName)
                                        .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                                        .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));

                        npc[0].customName(nameComponent);
                        npc[0].setCustomNameVisible(true);
                        npc[0].setProfession(Villager.Profession.MASON); // Different profession from vault NPCs
                        npc[0].setAI(false);
                        npc[0].setInvulnerable(true);
                        npc[0].setSilent(true);
                        npc[0].setRemoveWhenFarAway(false);
                        // Additional invulnerability settings
                        npc[0].setCollidable(false); // Prevents physical interaction
                        npc[0].setPersistent(true); // Makes the entity persistent
                    });

                    // Wait for entity to be created
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Thread interrupted", e);
                    }

                    // Store the NPC in database
                    int entityId = npc[0].getEntityId();
                    String npcId = UUID.randomUUID().toString();

                    // Use the location components directly, similar to VaultNPCManager
                    String worldName = location.getWorld().getName();
                    double x = location.getX();
                    double y = location.getY();
                    double z = location.getZ();

                    // Still store the consolidated location string for backwards compatibility
                    String locationStr = String.format("%f,%f,%f,%s", x, y, z, worldName);

                    context.insertInto(
                            DSL.table("trade_vault_npcs"),
                            DSL.field("id"),
                            DSL.field("trade_id"),
                            DSL.field("nation_id"),
                            DSL.field("coordinates"),
                            DSL.field("entity_id"),
                            DSL.field("world"),
                            DSL.field("x"),
                            DSL.field("y"),
                            DSL.field("z"),
                            DSL.field("is_for_sender"))
                            .values(
                                    npcId,
                                    tradeId,
                                    nationId,
                                    locationStr,
                                    entityId,
                                    worldName,
                                    x,
                                    y,
                                    z,
                                    isSender ? 1 : 0)
                            .execute();

                    tradeService.getEntityToTradeVault().put(entityId, tradeId);
                    return true;
                }
            });
        });
    }

    public void attemptDeleteTradeNPC(Player player, String nationId) {
        // Logic to find the closest trade NPC that the player is looking at
        // and delete it if it belongs to their nation

        // This is a simplified version - in a real implementation you'd want to use
        // raycasting to find the entity the player is looking at
        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-looking-for"));

        // Get the entity the player is looking at
        org.bukkit.entity.Entity target = player.getTargetEntity(5); // 5 blocks range
        if (target == null || !(target instanceof Villager)) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-not-found"));
            return;
        }

        int entityId = target.getEntityId();
        String tradeId = tradeService.getEntityToTradeVault().get(entityId);

        if (tradeId == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-not-trade"));
            return;
        }

        // Check if this NPC belongs to the player's nation
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                Record record = context.select()
                        .from("trade_vault_npcs")
                        .where(DSL.field("entity_id").eq(entityId))
                        .and(DSL.field("nation_id").eq(nationId))
                        .fetchOne();

                if (record == null) {
                    player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-not-yours"));
                    return null;
                }

                // Remove the entity
                target.remove();

                // Remove from database
                context.deleteFrom(DSL.table("trade_vault_npcs"))
                        .where(DSL.field("entity_id").eq(entityId))
                        .execute();

                // Remove from tracking map
                tradeService.getEntityToTradeVault().remove(entityId);

                player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-removed"));
                return null;
            }
        });
    }

    public void deleteTrade(Player player, String tradeId) {
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                // First remove any NPCs for this trade
                Result<Record> npcResults = context.select()
                        .from("trade_vault_npcs")
                        .where(DSL.field("trade_id").eq(tradeId))
                        .fetch();

                for (Record record : npcResults) {
                    int entityId = record.get("entity_id", Integer.class);

                    // Remove the entity on the main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (org.bukkit.entity.Entity entity : Bukkit.getWorlds().stream()
                                .flatMap(world -> world.getEntities().stream())
                                .collect(Collectors.toList())) {
                            if (entity.getEntityId() == entityId) {
                                entity.remove();
                                break;
                            }
                        }
                    });

                    // Remove from tracking map
                    tradeService.getEntityToTradeVault().remove(entityId);
                }

                // Delete trade vault NPCs
                context.deleteFrom(DSL.table("trade_vault_npcs"))
                        .where(DSL.field("trade_id").eq(tradeId))
                        .execute();

                // Delete trade vault
                context.deleteFrom(DSL.table("trade_vaults"))
                        .where(DSL.field("trade_id").eq(tradeId))
                        .execute();

                // Delete trade
                context.deleteFrom(DSL.table("trades"))
                        .where(DSL.field("id").eq(tradeId))
                        .execute();

                // Remove from memory
                tradeService.getActiveTrades().remove(tradeId);

                // Notify player
                player.sendMessage(plugin.getLocalizationManager().getComponent("trade.deleted"));

                return null;
            }
        });
    }
}

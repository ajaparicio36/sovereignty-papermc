package com.tatayless.sovereignty.services;

import com.google.gson.Gson;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.trade.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TradeService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final VaultService vaultService;
    private final Map<String, Trade> activeTrades = new HashMap<>();
    private final Map<UUID, TradeSession> playerSessions = new HashMap<>();
    private final Map<Integer, String> entityToTradeVault = new HashMap<>(); // Maps entity ID to trade vault ID

    // Specialized handlers
    private final TradeUIHandler uiHandler;
    private final TradeVaultHandler vaultHandler;
    private final TradeNPCHandler npcHandler;
    private final TradeExecutionHandler executionHandler;

    private final Gson gson = new Gson();

    public TradeService(Sovereignty plugin, NationService nationService, VaultService vaultService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.vaultService = vaultService;

        // Initialize handlers
        this.uiHandler = new TradeUIHandler(this, plugin);
        this.vaultHandler = new TradeVaultHandler(this, plugin);
        this.npcHandler = new TradeNPCHandler(this, plugin);
        this.executionHandler = new TradeExecutionHandler(this, plugin, vaultService, nationService);
    }

    /**
     * Initialize the trade service
     */
    public void initialize() {
        loadTrades();
        vaultHandler.setupPeriodicSaving();
    }

    public void loadTrades() {
        CompletableFuture.runAsync(() -> {
            plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
                @Override
                public Void execute(Connection conn, DSLContext context) throws SQLException {
                    // Load trades
                    Result<Record> results = context.select().from("trades").fetch();

                    for (Record record : results) {
                        String id = record.get("id", String.class);
                        String sendingNationId = record.get("sending_nation_id", String.class);
                        String receivingNationId = record.get("receiving_nation_id", String.class);
                        String statusStr = record.get("status", String.class);
                        int consecutiveTrades = record.get("consecutive_trades", Integer.class);

                        Trade.Status status;
                        try {
                            status = Trade.Status.valueOf(statusStr.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            status = Trade.Status.PENDING;
                        }

                        Trade trade = new Trade(id, sendingNationId, receivingNationId);
                        trade.setStatus(status);
                        trade.setConsecutiveTrades(consecutiveTrades);

                        // Handle dates - need different handling for MySQL vs SQLite
                        Object lastExecutionObj = record.get("last_execution");
                        if (lastExecutionObj != null) {
                            trade.setLastExecution(parseDateTime(lastExecutionObj));
                        }

                        // Find related trade vault for next execution date
                        Record vaultRecord = context.select()
                                .from("trade_vaults")
                                .where(DSL.field("trade_id").eq(id))
                                .fetchOne();

                        if (vaultRecord != null) {
                            Object nextExecutionObj = vaultRecord.get("next_execution");
                            int executionInterval = vaultRecord.get("execution_interval", Integer.class);

                            trade.setExecutionInterval(executionInterval);
                            if (nextExecutionObj != null) {
                                trade.setNextExecution(parseDateTime(nextExecutionObj));
                            }
                        }

                        activeTrades.put(id, trade);
                    }

                    // Load trade NPCs
                    Result<Record> npcResults = context.select().from("trade_vault_npcs").fetch();

                    for (Record record : npcResults) {
                        int entityId = record.get("entity_id", Integer.class);
                        String tradeId = record.get("trade_id", String.class);

                        // Associate entity with trade vault for quick lookup
                        entityToTradeVault.put(entityId, tradeId);
                    }

                    plugin.getLogger().info("Loaded " + activeTrades.size() + " trades from database");

                    // Start a scheduler to process trades
                    scheduleTradeProcessing();

                    return null;
                }
            });
        });
    }

    private Date parseDateTime(Object dateTimeObj) {
        if (dateTimeObj instanceof Timestamp) {
            return new Date(((Timestamp) dateTimeObj).getTime());
        } else if (dateTimeObj instanceof String) {
            // SQLite dates are stored as strings
            try {
                LocalDateTime ldt = LocalDateTime.parse((String) dateTimeObj);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to parse date: " + dateTimeObj);
            }
        }
        return null;
    }

    private void scheduleTradeProcessing() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processReadyTrades();
            }
        }.runTaskTimerAsynchronously(plugin, 1200, 1200); // Run every minute (20 ticks/sec * 60 sec)
    }

    private void processReadyTrades() {
        plugin.getLogger().info("Processing trades...");
        List<Trade> readyTrades = activeTrades.values().stream()
                .filter(Trade::isReady)
                .collect(Collectors.toList());

        if (!readyTrades.isEmpty()) {
            plugin.getLogger().info("Found " + readyTrades.size() + " trades ready for execution");
        }

        for (Trade trade : readyTrades) {
            executionHandler.executeTrade(trade);
        }
    }

    /**
     * Starts the trade execution task that runs periodically to check and execute
     * trades.
     * This method should be called during plugin startup.
     */
    public void startTradeExecutionTask() {
        scheduleTradeProcessing();
    }

    /**
     * Gets all trades associated with a specific nation.
     * 
     * @param nationId the nation ID to get trades for
     * @return List of Trade objects where the nation is either sending or receiving
     */
    public List<Trade> getNationTrades(String nationId) {
        return activeTrades.values().stream()
                .filter(trade -> trade.getSendingNationId().equals(nationId) ||
                        trade.getReceivingNationId().equals(nationId))
                .collect(Collectors.toList());
    }

    // UI-related methods - delegate to UIHandler
    public void openTradeCreationMenu(Player player, String senderNationId, String receiverNationId, int interval) {
        uiHandler.openTradeCreationMenu(player, senderNationId, receiverNationId, interval);
    }

    public void openTradeDeleteMenu(Player player, String nationId) {
        uiHandler.openTradeDeleteMenu(player, nationId);
    }

    public void openTradeConfirmDeleteMenu(Player player, String nationId, String tradeId) {
        uiHandler.openTradeConfirmDeleteMenu(player, nationId, tradeId);
    }

    public void openTradeListMenu(Player player, String nationId) {
        uiHandler.openTradeListMenu(player, nationId);
    }

    public void openTradeVault(Player player, String nationId, String tradeId) {
        vaultHandler.openTradeVault(player, nationId, tradeId);
    }

    public void openTradeNPCSelectionMenu(Player player, String nationId) {
        npcHandler.openTradeNPCSelectionMenu(player, nationId);
    }

    public CompletableFuture<Boolean> createTradeNPC(String nationId, String tradeId,
            org.bukkit.Location location, boolean isSender) {
        return npcHandler.createTradeNPC(nationId, tradeId, location, isSender);
    }

    public void attemptDeleteTradeNPC(Player player, String nationId) {
        npcHandler.attemptDeleteTradeNPC(player, nationId);
    }

    // Inventory event handling methods
    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        UUID playerUuid = player.getUniqueId();

        // Check if player has an active trade session
        if (!playerSessions.containsKey(playerUuid))
            return;

        TradeSession session = playerSessions.get(playerUuid);

        // Handle specific inventory types based on the inventory holder
        if (event.getInventory().getHolder() instanceof TradeVaultHandler.TradeVaultInventoryHolder) {
            vaultHandler.handleTradeVaultClick(event, session);
            return;
        }

        // Handle different inventory types based on session type
        switch (session.getType()) {
            case CREATE:
                uiHandler.handleTradeCreationClick(event, session);
                break;
            case LIST:
                uiHandler.handleTradeListClick(event, session);
                break;
            case DELETE_SELECT:
                uiHandler.handleTradeDeleteSelectClick(event, session);
                break;
            case DELETE_CONFIRM:
                uiHandler.handleTradeDeleteConfirmClick(event, session);
                break;
            case NPC_CREATE:
                npcHandler.handleTradeNPCCreateClick(event, session);
                break;
            case SENDING_VAULT:
            case RECEIVING_VAULT:
                // Handle vault interactions safely including buttons
                // This case should rarely be reached now that we check for
                // TradeVaultInventoryHolder
                vaultHandler.handleTradeVaultClick(event, session);
                break;
        }
    }

    public void handleInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Check if player has an active trade session
        if (!playerSessions.containsKey(playerUuid))
            return;

        TradeSession session = playerSessions.get(playerUuid);

        // If this was a vault inventory, update the contents
        if (event.getInventory().getHolder() instanceof TradeVaultHandler.TradeVaultInventoryHolder) {
            vaultHandler.updateTradeVault(player, session, event.getInventory());
            vaultHandler.handleInventoryClose(player, session);

            // Don't remove the session here, as the player may be navigating between trade
            // menus
        }
        // Only remove session if player is not navigating within trade menus
        else if (!player.getOpenInventory().title().toString().toLowerCase().contains("trade")) {
            playerSessions.remove(playerUuid);
        }
    }

    /**
     * Check for inventory changes and save if needed
     * Called after inventory interactions to ensure state is saved
     */
    public void checkAndSaveTradeInventoryChanges(Player player, TradeVaultHandler.TradeVaultInventoryHolder holder) {
        String tradeId = holder.getTradeId();
        boolean isSender = holder.isSender();

        // Get the inventory to check
        if (player.getOpenInventory() != null && player.getOpenInventory().getTopInventory() != null &&
                player.getOpenInventory().getTopInventory().getHolder() == holder) {

            TradeSession session = playerSessions.get(player.getUniqueId());
            if (session != null) {
                vaultHandler.updateTradeVault(player, session, player.getOpenInventory().getTopInventory());
                plugin.getLogger().info("[DEBUG] Checked and saved changes for trade vault " + tradeId +
                        " (sender: " + isSender + ") by player " + player.getName());
            }
        }
    }

    // Delete trade method
    public void deleteTrade(Player player, String tradeId) {
        npcHandler.deleteTrade(player, tradeId);
    }

    // Getters
    public Sovereignty getPlugin() {
        return plugin;
    }

    public NationService getNationService() {
        return nationService;
    }

    public VaultService getVaultService() {
        return vaultService;
    }

    public Map<String, Trade> getActiveTrades() {
        return activeTrades;
    }

    public Map<UUID, TradeSession> getPlayerSessions() {
        return playerSessions;
    }

    public Map<Integer, String> getEntityToTradeVault() {
        return entityToTradeVault;
    }

    public Gson getGson() {
        return gson;
    }

    public TradeVaultHandler getVaultHandler() {
        return vaultHandler;
    }

    public TradeUIHandler getUiHandler() {
        return uiHandler;
    }

    public TradeNPCHandler getNpcHandler() {
        return npcHandler;
    }

    public TradeExecutionHandler getExecutionHandler() {
        return executionHandler;
    }

    // Trade utils
    public String getTradeIdFromEntity(int entityId) {
        return entityToTradeVault.get(entityId);
    }

    public boolean isSenderEntity(int entityId) {
        String tradeId = entityToTradeVault.get(entityId);
        if (tradeId == null)
            return false;

        // Look up in database
        return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
            @Override
            public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                Record record = context.select()
                        .from("trade_vault_npcs")
                        .where(DSL.field("entity_id").eq(entityId))
                        .fetchOne();

                if (record == null)
                    return false;

                return record.get("is_for_sender", Integer.class) == 1;
            }
        });
    }

    // For compatibility with older code - can be removed after refactoring
    // TradeStatus enum for backward compatibility
    public enum TradeStatus {
        ACTIVE, PENDING, CANCELLED, COMPLETED;

        public Trade.Status toModelStatus() {
            return Trade.Status.valueOf(this.name());
        }

        public static TradeStatus fromModelStatus(Trade.Status status) {
            return TradeStatus.valueOf(status.name());
        }
    }

    /**
     * Activates a pending trade.
     * 
     * @param tradeId The ID of the trade to activate
     * @return true if the trade was activated, false if it doesn't exist or isn't
     *         pending
     */
    public boolean activateTrade(String tradeId) {
        Trade trade = activeTrades.get(tradeId);
        if (trade == null || trade.getStatus() != Trade.Status.PENDING) {
            return false;
        }

        trade.setStatus(Trade.Status.ACTIVE);

        // Update in database
        return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
            @Override
            public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                int updated = context.update(DSL.table("trades"))
                        .set(DSL.field("status"), "active")
                        .where(DSL.field("id").eq(tradeId))
                        .execute();
                return updated > 0;
            }
        });
    }
}

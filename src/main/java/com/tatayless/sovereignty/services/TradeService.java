package com.tatayless.sovereignty.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Nation;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
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

public class TradeService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final VaultService vaultService;
    private final Map<String, Trade> trades = new HashMap<>();
    private final Map<String, TradeVault> tradeVaults = new HashMap<>();
    private final Gson gson = new Gson();

    public TradeService(Sovereignty plugin, NationService nationService, VaultService vaultService) {
        this.plugin = plugin;
        this.nationService = nationService;
        this.vaultService = vaultService;
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
                        String status = record.get("status", String.class);
                        int consecutiveTrades = record.get("consecutive_trades", Integer.class);
                        Object lastExecutionObj = record.get("last_execution");

                        Date lastExecution = null;
                        if (lastExecutionObj != null) {
                            if (lastExecutionObj instanceof Timestamp) {
                                lastExecution = new Date(((Timestamp) lastExecutionObj).getTime());
                            } else if (lastExecutionObj instanceof String) {
                                // SQLite dates are stored as strings
                                LocalDateTime ldt = LocalDateTime.parse((String) lastExecutionObj);
                                lastExecution = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                            }
                        }

                        Trade trade = new Trade(id, sendingNationId, receivingNationId);
                        trade.setStatus(TradeStatus.valueOf(status.toUpperCase()));
                        trade.setConsecutiveTrades(consecutiveTrades);
                        trade.setLastExecution(lastExecution);

                        trades.put(id, trade);
                    }

                    // Load trade vaults
                    Result<Record> vaultResults = context.select().from("trade_vaults").fetch();

                    for (Record record : vaultResults) {
                        String id = record.get("id", String.class);
                        String tradeId = record.get("trade_id", String.class);
                        String sendingItemsJson = record.get("sending_items_vault", String.class);
                        String receivingItemsJson = record.get("receiving_items_vault", String.class);
                        int executionInterval = record.get("execution_interval", Integer.class);
                        Object nextExecutionObj = record.get("next_execution");

                        ItemStack[] sendingItems = null;
                        ItemStack[] receivingItems = null;
                        Date nextExecution = null;

                        if (sendingItemsJson != null && !sendingItemsJson.isEmpty()) {
                            List<Map<String, Object>> itemsList = gson.fromJson(sendingItemsJson,
                                    new TypeToken<List<Map<String, Object>>>() {
                                    }.getType());
                            sendingItems = deserializeItems(itemsList);
                        }

                        if (receivingItemsJson != null && !receivingItemsJson.isEmpty()) {
                            List<Map<String, Object>> itemsList = gson.fromJson(receivingItemsJson,
                                    new TypeToken<List<Map<String, Object>>>() {
                                    }.getType());
                            receivingItems = deserializeItems(itemsList);
                        }

                        if (nextExecutionObj != null) {
                            if (nextExecutionObj instanceof Timestamp) {
                                nextExecution = new Date(((Timestamp) nextExecutionObj).getTime());
                            } else if (nextExecutionObj instanceof String) {
                                // SQLite dates are stored as strings
                                LocalDateTime ldt = LocalDateTime.parse((String) nextExecutionObj);
                                nextExecution = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
                            }
                        }

                        TradeVault tradeVault = new TradeVault(id, tradeId, sendingItems, receivingItems,
                                executionInterval);
                        tradeVault.setNextExecution(nextExecution);
                        tradeVaults.put(tradeId, tradeVault);
                    }

                    plugin.getLogger().info("Loaded " + trades.size() + " trades and " +
                            tradeVaults.size() + " trade vaults from database");

                    return null;
                }
            });
        });
    }

    public void startTradeExecutionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Date now = new Date();
                List<TradeVault> vaultsToProcess = new ArrayList<>();

                // Find vaults ready for execution
                for (TradeVault vault : tradeVaults.values()) {
                    if (vault.getNextExecution() != null && vault.getNextExecution().before(now)) {
                        Trade trade = trades.get(vault.getTradeId());
                        if (trade != null && trade.getStatus() == TradeStatus.ACTIVE) {
                            vaultsToProcess.add(vault);
                        }
                    }
                }

                // Process each vault
                for (TradeVault vault : vaultsToProcess) {
                    executeTrade(vault.getTradeId());
                }
            }
        }.runTaskTimerAsynchronously(plugin, 1200, 1200); // Run every minute (20 ticks/sec * 60 sec)
    }

    public CompletableFuture<Trade> proposeTrade(String sendingNationId, String receivingNationId, String playerId) {
        Nation sendingNation = nationService.getNation(sendingNationId);
        Nation receivingNation = nationService.getNation(receivingNationId);

        if (sendingNation == null || receivingNation == null) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if player has permission (president or senator)
        if (!sendingNation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(null);
        }

        // Check if nations are at war
        if (plugin.getServiceManager().getWarService().isAtWar(sendingNationId, receivingNationId)) {
            return CompletableFuture.completedFuture(null);
        }

        // Generate trade ID
        String tradeId = UUID.randomUUID().toString();
        Trade trade = new Trade(tradeId, sendingNationId, receivingNationId);
        trade.setStatus(TradeStatus.PENDING);

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Trade>() {
                @Override
                public Trade execute(Connection conn, DSLContext context) throws SQLException {
                    // Insert trade record
                    context.insertInto(
                            DSL.table("trades"),
                            DSL.field("id"),
                            DSL.field("sending_nation_id"),
                            DSL.field("receiving_nation_id"),
                            DSL.field("status")).values(
                                    tradeId,
                                    sendingNationId,
                                    receivingNationId,
                                    "pending")
                            .execute();

                    trades.put(tradeId, trade);

                    // Create empty trade vault
                    String vaultId = UUID.randomUUID().toString();
                    int executionInterval = 3; // Default interval

                    // Calculate next execution time (3 MC days = 60 minutes in real time)
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.MINUTE, 60);
                    Date nextExecution = calendar.getTime();

                    context.insertInto(
                            DSL.table("trade_vaults"),
                            DSL.field("id"),
                            DSL.field("trade_id"),
                            DSL.field("execution_interval"),
                            DSL.field("next_execution")).values(
                                    vaultId,
                                    tradeId,
                                    executionInterval,
                                    new Timestamp(nextExecution.getTime()))
                            .execute();

                    TradeVault tradeVault = new TradeVault(vaultId, tradeId, null, null, executionInterval);
                    tradeVault.setNextExecution(nextExecution);
                    tradeVaults.put(tradeId, tradeVault);

                    // Notify receiving nation
                    Nation receivingNation2 = nationService.getNation(receivingNationId);
                    if (receivingNation2 != null) {
                        for (String officerId : getOfficerIds(receivingNation2)) {
                            notifyPlayer(officerId, plugin.getLocalizationManager().getMessage(
                                    "trade.received",
                                    "nation", sendingNation.getName()));
                        }
                    }

                    return trade;
                }
            });
        });
    }

    public CompletableFuture<Boolean> acceptTrade(String tradeId, String playerId) {
        Trade trade = trades.get(tradeId);
        if (trade == null || trade.getStatus() != TradeStatus.PENDING) {
            return CompletableFuture.completedFuture(false);
        }

        Nation receivingNation = nationService.getNation(trade.getReceivingNationId());
        if (receivingNation == null || !receivingNation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Update trade status
                    context.update(DSL.table("trades"))
                            .set(DSL.field("status"), "active")
                            .where(DSL.field("id").eq(tradeId))
                            .execute();

                    trade.setStatus(TradeStatus.ACTIVE);

                    // Notify nations
                    Nation sendingNation = nationService.getNation(trade.getSendingNationId());

                    if (sendingNation != null) {
                        for (String memberId : getAllMemberIds(sendingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.accepted",
                                    "nation", receivingNation.getName()));
                        }
                    }

                    for (String memberId : getAllMemberIds(receivingNation)) {
                        notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                "trade.accepted",
                                "nation", sendingNation.getName()));
                    }

                    return true;
                }
            });
        });
    }

    public CompletableFuture<Boolean> cancelTrade(String tradeId, String playerId) {
        Trade trade = trades.get(tradeId);
        if (trade == null || trade.getStatus() == TradeStatus.COMPLETED || trade.getStatus() == TradeStatus.CANCELLED) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has permission (officer from either nation)
        Nation sendingNation = nationService.getNation(trade.getSendingNationId());
        Nation receivingNation = nationService.getNation(trade.getReceivingNationId());

        if ((sendingNation == null || !sendingNation.isOfficer(playerId)) &&
                (receivingNation == null || !receivingNation.isOfficer(playerId))) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Update trade status
                    context.update(DSL.table("trades"))
                            .set(DSL.field("status"), "cancelled")
                            .where(DSL.field("id").eq(tradeId))
                            .execute();

                    trade.setStatus(TradeStatus.CANCELLED);

                    // Return items to nation vaults
                    TradeVault tradeVault = tradeVaults.get(tradeId);
                    if (tradeVault != null) {
                        // Return sending items to sending nation
                        if (tradeVault.getSendingItems() != null && tradeVault.getSendingItems().length > 0) {
                            returnItemsToNation(trade.getSendingNationId(), tradeVault.getSendingItems());
                        }

                        // Return receiving items to receiving nation
                        if (tradeVault.getReceivingItems() != null && tradeVault.getReceivingItems().length > 0) {
                            returnItemsToNation(trade.getReceivingNationId(), tradeVault.getReceivingItems());
                        }
                    }

                    // Notify nations
                    String cancellerNationName = sendingNation.isOfficer(playerId) ? sendingNation.getName()
                            : receivingNation.getName();

                    if (sendingNation != null && !sendingNation.getName().equals(cancellerNationName)) {
                        for (String memberId : getAllMemberIds(sendingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.cancelled",
                                    "nation", cancellerNationName));
                        }
                    }

                    if (receivingNation != null && !receivingNation.getName().equals(cancellerNationName)) {
                        for (String memberId : getAllMemberIds(receivingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.cancelled",
                                    "nation", cancellerNationName));
                        }
                    }

                    return true;
                }
            });
        });
    }

    public CompletableFuture<Boolean> executeTrade(String tradeId) {
        Trade trade = trades.get(tradeId);
        TradeVault tradeVault = tradeVaults.get(tradeId);

        if (trade == null || tradeVault == null || trade.getStatus() != TradeStatus.ACTIVE) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    Nation sendingNation = nationService.getNation(trade.getSendingNationId());
                    Nation receivingNation = nationService.getNation(trade.getReceivingNationId());

                    if (sendingNation == null || receivingNation == null) {
                        return false;
                    }

                    // Check if nations are at war
                    if (plugin.getServiceManager().getWarService().isAtWar(
                            trade.getSendingNationId(), trade.getReceivingNationId())) {
                        // Cancel the trade if nations are at war
                        cancelTrade(tradeId, sendingNation.getPresidentId());
                        return false;
                    }

                    // Check if both sides have supplied required items
                    boolean tradeSuccess = true;
                    if ((tradeVault.getSendingItems() == null || allEmpty(tradeVault.getSendingItems())) ||
                            (tradeVault.getReceivingItems() == null || allEmpty(tradeVault.getReceivingItems()))) {
                        tradeSuccess = false;
                    }

                    if (tradeSuccess) {
                        // Exchange items
                        transferItems(trade.getSendingNationId(), tradeVault.getReceivingItems());
                        transferItems(trade.getReceivingNationId(), tradeVault.getSendingItems());

                        // Update consecutive trades counter
                        trade.incrementConsecutiveTrades();

                        // Update trade in database
                        context.update(DSL.table("trades"))
                                .set(DSL.field("consecutive_trades"), trade.getConsecutiveTrades())
                                .set(DSL.field("last_execution"), new Timestamp(System.currentTimeMillis()))
                                .where(DSL.field("id").eq(tradeId))
                                .execute();

                        // Update nation power if needed
                        if (trade.getConsecutiveTrades() % 3 == 0) {
                            // +0.05 power for every 3 consecutive trades
                            double sendingPower = sendingNation.getPower() + 0.05;
                            double receivingPower = receivingNation.getPower() + 0.05;

                            sendingNation.setPower(sendingPower);
                            receivingNation.setPower(receivingPower);

                            nationService.saveNation(sendingNation);
                            nationService.saveNation(receivingNation);
                        }

                        // Notify nations
                        for (String memberId : getAllMemberIds(sendingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.completed",
                                    "nation", receivingNation.getName()));
                        }

                        for (String memberId : getAllMemberIds(receivingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.completed",
                                    "nation", sendingNation.getName()));
                        }
                    } else {
                        // Failed trade
                        trade.resetConsecutiveTrades();

                        // Return items to their owners
                        if (tradeVault.getSendingItems() != null && !allEmpty(tradeVault.getSendingItems())) {
                            returnItemsToNation(trade.getSendingNationId(), tradeVault.getSendingItems());
                        }

                        if (tradeVault.getReceivingItems() != null && !allEmpty(tradeVault.getReceivingItems())) {
                            returnItemsToNation(trade.getReceivingNationId(), tradeVault.getReceivingItems());
                        }

                        // Update trade in database
                        context.update(DSL.table("trades"))
                                .set(DSL.field("consecutive_trades"), 0)
                                .set(DSL.field("last_execution"), new Timestamp(System.currentTimeMillis()))
                                .where(DSL.field("id").eq(tradeId))
                                .execute();

                        // Notify nations of failure
                        for (String memberId : getAllMemberIds(sendingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.failed",
                                    "nation", receivingNation.getName()));
                        }

                        for (String memberId : getAllMemberIds(receivingNation)) {
                            notifyPlayer(memberId, plugin.getLocalizationManager().getMessage(
                                    "trade.failed",
                                    "nation", sendingNation.getName()));
                        }
                    }

                    // Reset trade vault
                    tradeVault.setSendingItems(null);
                    tradeVault.setReceivingItems(null);

                    // Calculate next execution time
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.MINUTE, tradeVault.getExecutionInterval() * 20); // Convert MC days to minutes
                    tradeVault.setNextExecution(calendar.getTime());

                    // Update vault in database
                    updateTradeVault(tradeVault);

                    return tradeSuccess;
                }
            });
        });
    }

    public void openTradeVault(String tradeId, String playerId, boolean isSendingSide) {
        Trade trade = trades.get(tradeId);
        if (trade == null || trade.getStatus() != TradeStatus.ACTIVE) {
            return;
        }

        String nationId = isSendingSide ? trade.getSendingNationId() : trade.getReceivingNationId();
        Nation nation = nationService.getNation(nationId);

        if (nation == null || !nation.isMember(playerId)) {
            return;
        }

        TradeVault tradeVault = tradeVaults.get(tradeId);
        if (tradeVault == null) {
            return;
        }

        // Get the other nation's name for display
        String otherNationId = isSendingSide ? trade.getReceivingNationId() : trade.getSendingNationId();
        Nation otherNation = nationService.getNation(otherNationId);
        String otherNationName = otherNation != null ? otherNation.getName() : "Unknown";

        // Open inventory UI in main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            int rows = plugin.getConfigManager().getTradeVaultRows();
            int size = rows * 9;

            String title = isSendingSide ? "Trade Sending to: " + otherNationName
                    : "Trade Receiving from: " + otherNationName;

            org.bukkit.inventory.Inventory inventory = Bukkit.createInventory(
                    null,
                    size,
                    net.kyori.adventure.text.Component.text(title));

            ItemStack[] items = isSendingSide ? tradeVault.getSendingItems() : tradeVault.getReceivingItems();
            if (items != null) {
                inventory.setContents(items);
            }

            org.bukkit.entity.Player player = Bukkit.getPlayer(UUID.fromString(playerId));
            if (player != null) {
                player.openInventory(inventory);
            }
        });
    }

    public void updateTradeVaultContents(String tradeId, boolean isSendingSide, ItemStack[] contents) {
        TradeVault tradeVault = tradeVaults.get(tradeId);
        if (tradeVault == null) {
            return;
        }

        if (isSendingSide) {
            tradeVault.setSendingItems(contents);
        } else {
            tradeVault.setReceivingItems(contents);
        }

        updateTradeVault(tradeVault);
    }

    private CompletableFuture<Boolean> updateTradeVault(TradeVault vault) {
        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Serialize items
                    String sendingItemsJson = null;
                    String receivingItemsJson = null;

                    if (vault.getSendingItems() != null) {
                        sendingItemsJson = gson.toJson(serializeItems(vault.getSendingItems()));
                    }

                    if (vault.getReceivingItems() != null) {
                        receivingItemsJson = gson.toJson(serializeItems(vault.getReceivingItems()));
                    }

                    // Update vault
                    context.update(DSL.table("trade_vaults"))
                            .set(DSL.field("sending_items_vault"), sendingItemsJson)
                            .set(DSL.field("receiving_items_vault"), receivingItemsJson)
                            .set(DSL.field("next_execution"),
                                    vault.getNextExecution() != null ? new Timestamp(vault.getNextExecution().getTime())
                                            : null)
                            .where(DSL.field("id").eq(vault.getId()))
                            .execute();

                    return true;
                }
            });
        });
    }

    private void transferItems(String nationId, ItemStack[] items) {
        if (items == null || allEmpty(items)) {
            return;
        }

        // Get nation vault
        vaultService.getOrCreateVault(nationId).thenAccept(vault -> {
            if (vault != null) {
                // Try to add items to the main vault
                ItemStack[] remainingItems = mergeIntoVault(vault.getItems(), items);

                if (!allEmpty(remainingItems)) {
                    // Store overflow items
                    vault.setOverflowItems(remainingItems);

                    // Set expiry time
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.MINUTE, plugin.getConfigManager().getVaultExpiryTimeMinutes());
                    vault.setOverflowExpiry(calendar.getTime());
                }

                vaultService.saveVault(vault);
            }
        });
    }

    private void returnItemsToNation(String nationId, ItemStack[] items) {
        transferItems(nationId, items);
    }

    private ItemStack[] mergeIntoVault(ItemStack[] vaultItems, ItemStack[] newItems) {
        if (vaultItems == null) {
            int rows = plugin.getConfigManager().getNationVaultRows();
            vaultItems = new ItemStack[rows * 9];
        }

        List<ItemStack> overflow = new ArrayList<>();

        // Try to merge items into the vault
        for (ItemStack item : newItems) {
            if (item == null)
                continue;

            boolean added = false;

            // First try to stack with existing items
            for (int i = 0; i < vaultItems.length; i++) {
                if (vaultItems[i] != null && vaultItems[i].isSimilar(item) &&
                        vaultItems[i].getAmount() < vaultItems[i].getMaxStackSize()) {
                    int space = vaultItems[i].getMaxStackSize() - vaultItems[i].getAmount();
                    if (space >= item.getAmount()) {
                        // Can fit the entire stack
                        vaultItems[i].setAmount(vaultItems[i].getAmount() + item.getAmount());
                        added = true;
                        break;
                    } else {
                        // Can fit part of the stack
                        vaultItems[i].setAmount(vaultItems[i].getMaxStackSize());
                        ItemStack remainder = item.clone();
                        remainder.setAmount(item.getAmount() - space);
                        item = remainder; // Continue with remainder
                    }
                }
            }

            // If not added or partially added, try to find an empty slot
            if (!added) {
                for (int i = 0; i < vaultItems.length; i++) {
                    if (vaultItems[i] == null) {
                        vaultItems[i] = item.clone();
                        added = true;
                        break;
                    }
                }
            }

            // If still not added, add to overflow
            if (!added) {
                overflow.add(item.clone());
            }
        }

        // Return overflow items
        if (overflow.isEmpty()) {
            return null;
        } else {
            return overflow.toArray(new ItemStack[0]);
        }
    }

    private boolean allEmpty(ItemStack[] items) {
        if (items == null)
            return true;

        for (ItemStack item : items) {
            if (item != null) {
                return false;
            }
        }
        return true;
    }

    private List<String> getOfficerIds(Nation nation) {
        List<String> officers = new ArrayList<>();

        if (nation.getPresidentId() != null) {
            officers.add(nation.getPresidentId());
        }

        officers.addAll(nation.getSenators());

        return officers;
    }

    private List<String> getAllMemberIds(Nation nation) {
        List<String> members = new ArrayList<>();

        if (nation.getPresidentId() != null) {
            members.add(nation.getPresidentId());
        }

        members.addAll(nation.getSenators());
        members.addAll(nation.getSoldiers());
        members.addAll(nation.getCitizens());

        return members;
    }

    private void notifyPlayer(String playerId, String message) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(UUID.fromString(playerId));
        if (player != null && player.isOnline()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent(message));
        }
    }

    private ItemStack[] deserializeItems(List<Map<String, Object>> itemsList) {
        if (itemsList == null)
            return null;

        List<ItemStack> items = new ArrayList<>();
        for (Map<String, Object> itemMap : itemsList) {
            try {
                ItemStack item = ItemStack.deserialize(itemMap);
                items.add(item);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to deserialize item: " + e.getMessage());
            }
        }

        return items.toArray(new ItemStack[0]);
    }

    private List<Map<String, Object>> serializeItems(ItemStack[] items) {
        if (items == null)
            return null;

        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                try {
                    Map<String, Object> serialized = item.serialize();
                    itemsList.add(serialized);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to serialize item: " + e.getMessage());
                }
            }
        }

        return itemsList;
    }

    public List<Trade> getTrades() {
        return new ArrayList<>(trades.values());
    }

    public List<Trade> getNationTrades(String nationId) {
        List<Trade> nationTrades = new ArrayList<>();
        for (Trade trade : trades.values()) {
            if (trade.getSendingNationId().equals(nationId) || trade.getReceivingNationId().equals(nationId)) {
                nationTrades.add(trade);
            }
        }
        return nationTrades;
    }

    public Trade getTrade(String tradeId) {
        return trades.get(tradeId);
    }

    public TradeVault getTradeVault(String tradeId) {
        return tradeVaults.get(tradeId);
    }

    public static class Trade {
        private final String id;
        private final String sendingNationId;
        private final String receivingNationId;
        private TradeStatus status;
        private int consecutiveTrades;
        private Date lastExecution;

        public Trade(String id, String sendingNationId, String receivingNationId) {
            this.id = id;
            this.sendingNationId = sendingNationId;
            this.receivingNationId = receivingNationId;
            this.status = TradeStatus.PENDING;
            this.consecutiveTrades = 0;
        }

        public String getId() {
            return id;
        }

        public String getSendingNationId() {
            return sendingNationId;
        }

        public String getReceivingNationId() {
            return receivingNationId;
        }

        public TradeStatus getStatus() {
            return status;
        }

        public void setStatus(TradeStatus status) {
            this.status = status;
        }

        public int getConsecutiveTrades() {
            return consecutiveTrades;
        }

        public void setConsecutiveTrades(int consecutiveTrades) {
            this.consecutiveTrades = consecutiveTrades;
        }

        public void incrementConsecutiveTrades() {
            this.consecutiveTrades++;
        }

        public void resetConsecutiveTrades() {
            this.consecutiveTrades = 0;
        }

        public Date getLastExecution() {
            return lastExecution;
        }

        public void setLastExecution(Date lastExecution) {
            this.lastExecution = lastExecution;
        }
    }

    public static class TradeVault {
        private final String id;
        private final String tradeId;
        private ItemStack[] sendingItems;
        private ItemStack[] receivingItems;
        private final int executionInterval;
        private Date nextExecution;

        public TradeVault(String id, String tradeId, ItemStack[] sendingItems,
                ItemStack[] receivingItems, int executionInterval) {
            this.id = id;
            this.tradeId = tradeId;
            this.sendingItems = sendingItems;
            this.receivingItems = receivingItems;
            this.executionInterval = executionInterval;
        }

        public String getId() {
            return id;
        }

        public String getTradeId() {
            return tradeId;
        }

        public ItemStack[] getSendingItems() {
            return sendingItems;
        }

        public void setSendingItems(ItemStack[] sendingItems) {
            this.sendingItems = sendingItems;
        }

        public ItemStack[] getReceivingItems() {
            return receivingItems;
        }

        public void setReceivingItems(ItemStack[] receivingItems) {
            this.receivingItems = receivingItems;
        }

        public int getExecutionInterval() {
            return executionInterval;
        }

        public Date getNextExecution() {
            return nextExecution;
        }

        public void setNextExecution(Date nextExecution) {
            this.nextExecution = nextExecution;
        }
    }

    public enum TradeStatus {
        PENDING,
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}

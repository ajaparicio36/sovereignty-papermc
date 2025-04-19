package com.tatayless.sovereignty.services.trade;

import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.NationService;
import com.tatayless.sovereignty.services.TradeService;
import com.tatayless.sovereignty.services.VaultService;
import org.bukkit.inventory.ItemStack;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

public class TradeExecutionHandler {
    private final TradeService tradeService;
    private final Sovereignty plugin;
    private final VaultService vaultService;
    private final NationService nationService;

    public TradeExecutionHandler(TradeService tradeService, Sovereignty plugin, VaultService vaultService,
            NationService nationService) {
        this.tradeService = tradeService;
        this.plugin = plugin;
        this.vaultService = vaultService;
        this.nationService = nationService;
    }

    public void executeTrade(Trade trade) {
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                // Get trade vault
                Record vaultRecord = context.select()
                        .from("trade_vaults")
                        .where(DSL.field("trade_id").eq(trade.getId()))
                        .fetchOne();

                if (vaultRecord == null) {
                    plugin.getLogger().warning("Trade vault not found for trade: " + trade.getId());
                    return null;
                }

                String sendingItemsJson = vaultRecord.get("sending_items_vault", String.class);
                final boolean[] tradeSuccess = { false };
                final double[] tradeItemRatio = { 0.0 };

                if (sendingItemsJson != null && !sendingItemsJson.isEmpty()) {
                    // Check if required items are present
                    List<Map<String, Object>> requiredItemsList = tradeService.getGson().fromJson(sendingItemsJson,
                            new TypeToken<List<Map<String, Object>>>() {
                            }.getType());

                    // Calculate the trade item ratio before transferring
                    ItemStack[] items = TradeItemsUtil.deserializeItems(requiredItemsList, plugin);
                    tradeItemRatio[0] = calculateTradeItemRatio(items);

                    // Transfer items from sender to receiver's vault
                    Nation receivingNation = nationService.getNation(trade.getReceivingNationId());
                    if (receivingNation != null) {
                        // Get or create receiver's vault
                        vaultService.getOrCreateVault(trade.getReceivingNationId()).thenAccept(receiverVault -> {
                            if (receiverVault != null) {
                                try {
                                    // Add items to receiver's vault overflow
                                    List<ItemStack> itemsList = Arrays.stream(items)
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.toList());

                                    receiverVault.addOverflowItems(itemsList,
                                            plugin.getConfigManager().getVaultExpiryTimeMinutes());
                                    vaultService.saveVault(receiverVault);

                                    // Mark trade successful
                                    tradeSuccess[0] = true;
                                } catch (Exception e) {
                                    plugin.getLogger().severe("Error processing trade items: " + e.getMessage());
                                }
                            }
                        });
                    }
                }

                // Update trade status
                if (tradeSuccess[0]) {
                    // Set trade to ACTIVE if it was PENDING
                    if (trade.getStatus() == Trade.Status.PENDING) {
                        trade.setStatus(Trade.Status.ACTIVE);
                    }

                    // Increment consecutive trades
                    trade.incrementConsecutiveTrades();
                    trade.setLastExecution(new Date());

                    // Calculate next execution time
                    Calendar calendar = Calendar.getInstance();
                    // In Minecraft, a day is 24000 ticks (20min real time)
                    // So 1 Minecraft day = 20 real minutes
                    calendar.add(Calendar.MINUTE, 20 * trade.getExecutionInterval());
                    trade.setNextExecution(calendar.getTime());

                    // Award power if consecutive trades reached threshold
                    int consecutiveNeeded = plugin.getConfigManager().getTradeConsecutiveForPower();
                    if (trade.getConsecutiveTrades() >= consecutiveNeeded) {
                        // Award power to both nations, scaled by trade item ratio
                        Nation sendingNation = nationService.getNation(trade.getSendingNationId());
                        Nation receivingNation = nationService.getNation(trade.getReceivingNationId());

                        if (sendingNation != null && receivingNation != null) {
                            double powerIncrement = plugin.getConfigManager().getTradePowerIncrement();

                            // Apply ratio to power gained (cannot be less than 10% of original value)
                            double adjustedPowerIncrement = Math.max(
                                    powerIncrement * tradeItemRatio[0],
                                    powerIncrement * 0.1);

                            // Log the power adjustment
                            plugin.getLogger().info("Trade " + trade.getId() + " power adjustment: " +
                                    "Original: " + powerIncrement + ", Ratio: " + tradeItemRatio[0] +
                                    ", Adjusted: " + adjustedPowerIncrement);

                            sendingNation.addPower(adjustedPowerIncrement);
                            receivingNation.addPower(adjustedPowerIncrement);

                            nationService.saveNation(sendingNation);
                            nationService.saveNation(receivingNation);

                            // Reset consecutive trades counter after awarding power
                            trade.setConsecutiveTrades(0);
                        }
                    }

                    // Update trade in database
                    context.update(DSL.table("trades"))
                            .set(DSL.field("consecutive_trades"), trade.getConsecutiveTrades())
                            .set(DSL.field("last_execution"), new Timestamp(trade.getLastExecution().getTime()))
                            .set(DSL.field("status"), trade.getStatus().toString().toLowerCase())
                            .where(DSL.field("id").eq(trade.getId()))
                            .execute();

                    // Update next_execution in trade_vaults
                    context.update(DSL.table("trade_vaults"))
                            .set(DSL.field("next_execution"), new Timestamp(trade.getNextExecution().getTime()))
                            .where(DSL.field("trade_id").eq(trade.getId()))
                            .execute();

                    plugin.getLogger().info("Trade " + trade.getId() + " executed successfully");
                } else {
                    plugin.getLogger().info("Trade " + trade.getId() + " failed to execute - items not ready");
                }

                return null;
            }
        });
    }

    /**
     * Calculates the ratio of actual items to the maximum possible volume
     * Max volume is defined as 9 slots of full stacks (9 * 64 = 576 items)
     * 
     * @param items The items in the trade
     * @return A ratio between 0.0 and 1.0
     */
    private double calculateTradeItemRatio(ItemStack[] items) {
        if (items == null) {
            return 0.0;
        }

        int totalItems = 0;
        int maxStackSize = 64;
        int maxSlots = 9;

        for (ItemStack item : items) {
            if (item != null) {
                totalItems += item.getAmount();
            }
        }

        int maxPossibleItems = maxSlots * maxStackSize; // 9 slots × 64 stack size = 576 items
        double ratio = (double) totalItems / maxPossibleItems;

        // Cap at 1.0 maximum
        return Math.min(ratio, 1.0);
    }
}

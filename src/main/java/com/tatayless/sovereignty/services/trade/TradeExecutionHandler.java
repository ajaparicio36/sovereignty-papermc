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

                if (sendingItemsJson != null && !sendingItemsJson.isEmpty()) {
                    // Check if required items are present
                    List<Map<String, Object>> requiredItemsList = tradeService.getGson().fromJson(sendingItemsJson,
                            new TypeToken<List<Map<String, Object>>>() {
                            }.getType());

                    // Transfer items from sender to receiver's vault
                    Nation receivingNation = nationService.getNation(trade.getReceivingNationId());
                    if (receivingNation != null) {
                        // Get or create receiver's vault
                        vaultService.getOrCreateVault(trade.getReceivingNationId()).thenAccept(receiverVault -> {
                            if (receiverVault != null) {
                                try {
                                    // Deserialize the items
                                    ItemStack[] items = TradeItemsUtil.deserializeItems(requiredItemsList, plugin);

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
                        // Award power to both nations
                        Nation sendingNation = nationService.getNation(trade.getSendingNationId());
                        Nation receivingNation = nationService.getNation(trade.getReceivingNationId());

                        if (sendingNation != null && receivingNation != null) {
                            double powerIncrement = plugin.getConfigManager().getTradePowerIncrement();
                            sendingNation.addPower(powerIncrement);
                            receivingNation.addPower(powerIncrement);

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
}

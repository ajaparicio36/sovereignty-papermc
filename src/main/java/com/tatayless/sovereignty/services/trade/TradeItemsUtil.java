package com.tatayless.sovereignty.services.trade;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for handling trade items and operations
 */
public class TradeItemsUtil {

    /**
     * Extract the trade ID from the lore of an item
     * 
     * @param lore Item lore to search through
     * @return The trade ID or null if not found
     */
    public static String extractTradeIdFromLore(List<net.kyori.adventure.text.Component> lore) {
        if (lore == null)
            return null;

        for (net.kyori.adventure.text.Component component : lore) {
            String plain = component.toString();
            if (plain.contains("ID:")) {
                String[] parts = plain.split("ID:");
                if (parts.length > 1) {
                    return parts[1].trim();
                }
            }
        }
        return null;
    }

    /**
     * Calculate the total number of items in an array of ItemStacks
     * 
     * @param items Array of ItemStacks to count
     * @return Total count of items
     */
    public static int calculateTotalItemCount(ItemStack[] items) {
        if (items == null)
            return 0;

        int total = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /**
     * Calculate the maximum item volume for trading based on rows
     * 
     * @param rows Number of inventory rows
     * @return Maximum item count
     */
    public static int calculateMaxItemVolume(int rows) {
        // 9 slots per row, assume max stack size of 64 for simplicity
        return rows * 9 * 64;
    }

    /**
     * Creates a new trade in the database and game system
     * 
     * @param plugin           The main plugin instance
     * @param tradeService     The trade service
     * @param player           The player creating the trade
     * @param senderNationId   The sending nation ID
     * @param receiverNationId The receiving nation ID
     * @param items            The items to initially add to the trade
     * @param interval         The execution interval in hours
     */
    public static void createTrade(Sovereignty plugin, TradeService tradeService, Player player,
            String senderNationId, String receiverNationId,
            List<ItemStack> items, int interval) {

        String tradeId = UUID.randomUUID().toString();

        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                // Insert into trades table
                context.insertInto(
                        DSL.table("trades"),
                        DSL.field("id"),
                        DSL.field("sending_nation_id"),
                        DSL.field("receiving_nation_id"),
                        DSL.field("status"),
                        DSL.field("consecutive_trades")).values(
                                tradeId,
                                senderNationId,
                                receiverNationId,
                                "PENDING",
                                0)
                        .execute();

                // Create trade vault
                context.insertInto(
                        DSL.table("trade_vaults"),
                        DSL.field("trade_id"),
                        DSL.field("execution_interval")).values(
                                tradeId,
                                interval)
                        .execute();

                return null;
            }
        });

        // Save the items to the sender's vault
        ItemStack[] itemArray = items.toArray(new ItemStack[0]);
        tradeService.getVaultHandler().saveTradeVaultItems(tradeId, true, itemArray);

        // Create the trade object in memory
        Trade trade = new Trade(tradeId, senderNationId, receiverNationId);
        trade.setStatus(Trade.Status.PENDING);
        trade.setExecutionInterval(interval);
        tradeService.getActiveTrades().put(tradeId, trade);

        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.created"));
        player.closeInventory();
    }
}

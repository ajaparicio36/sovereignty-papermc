package com.tatayless.sovereignty.services.trade;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TradeItemsUtil {

    public static ItemStack[] deserializeItems(List<Map<String, Object>> itemsList, Sovereignty plugin) {
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

    public static List<Map<String, Object>> serializeItems(ItemStack[] items) {
        if (items == null)
            return null;

        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                try {
                    Map<String, Object> serialized = item.serialize();
                    itemsList.add(serialized);
                } catch (Exception e) {
                    // Log error
                }
            }
        }

        return itemsList;
    }

    public static String extractTradeIdFromLore(List<net.kyori.adventure.text.Component> lore) {
        for (net.kyori.adventure.text.Component component : lore) {
            String plain = component.toString();
            if (plain.startsWith("ID: ")) {
                return plain.substring(4);
            }
        }
        return null;
    }

    public static ItemStack createTradeItem(TradeService tradeService, Trade trade) {
        Nation sendingNation = tradeService.getNationService().getNation(trade.getSendingNationId());
        Nation receivingNation = tradeService.getNationService().getNation(trade.getReceivingNationId());
        String senderName = sendingNation != null ? sendingNation.getName() : "Unknown";
        String receiverName = receivingNation != null ? receivingNation.getName() : "Unknown";

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(senderName + " → " + receiverName)
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Status: " + trade.getStatus().toString())
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text("Consecutive: " + trade.getConsecutiveTrades())
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));

        if (trade.getNextExecution() != null) {
            // Format next execution time
            String nextExec = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(trade.getNextExecution());
            lore.add(net.kyori.adventure.text.Component.text("Next: " + nextExec)
                    .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }

        // Add trade ID as hidden lore for identification
        lore.add(net.kyori.adventure.text.Component.text("ID: " + trade.getId())
                .color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static CompletableFuture<Boolean> createTrade(Sovereignty plugin, TradeService tradeService, Player player,
            String senderNationId, String receiverNationId,
            List<ItemStack> items, int interval) {

        return CompletableFuture.supplyAsync(() -> {
            return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Boolean>() {
                @Override
                public Boolean execute(Connection conn, DSLContext context) throws SQLException {
                    // Generate IDs
                    String tradeId = UUID.randomUUID().toString();
                    String vaultId = UUID.randomUUID().toString();

                    // Create trade record
                    context.insertInto(
                            DSL.table("trades"),
                            DSL.field("id"),
                            DSL.field("sending_nation_id"),
                            DSL.field("receiving_nation_id"),
                            DSL.field("status"))
                            .values(
                                    tradeId,
                                    senderNationId,
                                    receiverNationId,
                                    "pending")
                            .execute();

                    // Serialize items
                    List<Map<String, Object>> serializedItems = serializeItems(
                            items.toArray(new ItemStack[0]));
                    String itemsJson = tradeService.getGson().toJson(serializedItems);

                    // Calculate next execution time
                    Calendar calendar = Calendar.getInstance();
                    // In Minecraft, a day is 24000 ticks (20min real time)
                    calendar.add(Calendar.MINUTE, 20 * interval);
                    Date nextExecution = calendar.getTime();

                    // Create trade vault record
                    context.insertInto(
                            DSL.table("trade_vaults"),
                            DSL.field("id"),
                            DSL.field("trade_id"),
                            DSL.field("sending_items_vault"),
                            DSL.field("execution_interval"),
                            DSL.field("next_execution"))
                            .values(
                                    vaultId,
                                    tradeId,
                                    itemsJson,
                                    interval,
                                    new Timestamp(nextExecution.getTime()))
                            .execute();

                    // Create trade object and add to memory
                    Trade trade = new Trade(tradeId, senderNationId, receiverNationId);
                    trade.setExecutionInterval(interval);
                    trade.setNextExecution(nextExecution);
                    tradeService.getActiveTrades().put(tradeId, trade);

                    // Notify player
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.created"));
                    });

                    return true;
                }
            });
        });
    }
}

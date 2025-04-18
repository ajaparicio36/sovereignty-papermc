package com.tatayless.sovereignty.services.trade;

import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class TradeVaultHandler {
    private final TradeService tradeService;
    private final Sovereignty plugin;

    public TradeVaultHandler(TradeService tradeService, Sovereignty plugin) {
        this.tradeService = tradeService;
        this.plugin = plugin;
    }

    public void openTradeVault(Player player, String nationId, String tradeId) {
        Trade trade = tradeService.getActiveTrades().get(tradeId);
        if (trade == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.not-found"));
            return;
        }

        // Check if this nation is involved in the trade
        boolean isSender = trade.getSendingNationId().equals(nationId);
        boolean isReceiver = trade.getReceivingNationId().equals(nationId);

        if (!isSender && !isReceiver) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.not-participant"));
            return;
        }

        // Get trade vault data
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                Record vaultRecord = context.select()
                        .from("trade_vaults")
                        .where(DSL.field("trade_id").eq(tradeId))
                        .fetchOne();

                if (vaultRecord == null) {
                    player.sendMessage(plugin.getLocalizationManager().getComponent("trade.vault-not-found"));
                    return null;
                }

                String itemsJson = vaultRecord.get(isSender ? "sending_items_vault" : "receiving_items_vault",
                        String.class);
                int rows = plugin.getConfigManager().getTradeVaultRows();

                // Create inventory for trade vault
                Inventory inventory = Bukkit.createInventory(null, rows * 9,
                        net.kyori.adventure.text.Component
                                .text(isSender ? "Trade Vault (Sending)" : "Trade Vault (Receiving)"));

                // Populate with items if any
                if (itemsJson != null && !itemsJson.isEmpty()) {
                    List<Map<String, Object>> itemsList = tradeService.getGson().fromJson(itemsJson,
                            new TypeToken<List<Map<String, Object>>>() {
                            }.getType());
                    ItemStack[] items = TradeItemsUtil.deserializeItems(itemsList, plugin);

                    if (items != null && items.length > 0) {
                        for (int i = 0; i < Math.min(items.length, inventory.getSize()); i++) {
                            if (items[i] != null) {
                                inventory.setItem(i, items[i]);
                            }
                        }
                    }
                }

                // Open the inventory on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.openInventory(inventory);

                    // Create a session for this vault interaction
                    TradeSession session = new TradeSession(
                            isSender ? TradeSessionType.SENDING_VAULT : TradeSessionType.RECEIVING_VAULT,
                            nationId,
                            isSender ? trade.getReceivingNationId() : trade.getSendingNationId(),
                            0);
                    session.setTradeId(tradeId);
                    tradeService.getPlayerSessions().put(player.getUniqueId(), session);
                });

                return null;
            }
        });
    }

    public void updateTradeVault(Player player, TradeSession session, Inventory inventory) {
        boolean isSendingVault = session.getType() == TradeSessionType.SENDING_VAULT;
        String fieldName = isSendingVault ? "sending_items_vault" : "receiving_items_vault";

        // Serialize inventory contents
        List<Map<String, Object>> serializedItems = TradeItemsUtil.serializeItems(inventory.getContents());
        String itemsJson = tradeService.getGson().toJson(serializedItems);

        // Update database
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                context.update(DSL.table("trade_vaults"))
                        .set(DSL.field(fieldName), itemsJson)
                        .where(DSL.field("trade_id").eq(session.getTradeId()))
                        .execute();
                return null;
            }
        });

        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.vault-updated"));
    }
}

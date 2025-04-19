package com.tatayless.sovereignty.services.trade;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.DatabaseOperation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TradeVaultHandler {

    private final TradeService tradeService;
    private final Sovereignty plugin;
    private final Gson gson;

    // Constants for navigation buttons
    public static final int CONFIRM_BUTTON_SLOT = 49; // Middle of bottom row
    public static final int INFO_BUTTON_SLOT = 4; // Middle of top row

    // Cache for vault items
    private final Map<String, Map<Boolean, ItemStack[]>> tradeVaultItems = new HashMap<>();

    public TradeVaultHandler(TradeService tradeService, Sovereignty plugin) {
        this.tradeService = tradeService;
        this.plugin = plugin;
        this.gson = new Gson();
    }

    public void openTradeVault(Player player, String nationId, String tradeId) {
        Trade trade = tradeService.getActiveTrades().get(tradeId);
        if (trade == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.no_trade_found"));
            return;
        }

        boolean isSender = trade.getSendingNationId().equals(nationId);
        String title = isSender ? "Trade Sending Vault" : "Trade Receiving Vault";

        // Create inventory
        Inventory inventory = Bukkit.createInventory(null, 54,
                net.kyori.adventure.text.Component.text(title + ": " + trade.getId()));

        // Retrieve items from the vault
        ItemStack[] vaultItems = getTradeVaultItems(tradeId, isSender);
        if (vaultItems != null) {
            // Only copy items that aren't in special slots
            for (int i = 0; i < vaultItems.length && i < 54; i++) {
                if (i != CONFIRM_BUTTON_SLOT && i != INFO_BUTTON_SLOT) {
                    inventory.setItem(i, vaultItems[i]);
                }
            }
        }

        // Add info button
        inventory.setItem(INFO_BUTTON_SLOT, createInfoButton(trade, isSender));

        // Add confirm button
        inventory.setItem(CONFIRM_BUTTON_SLOT, createConfirmButton());

        // Open inventory and create session
        player.openInventory(inventory);

        TradeSessionType sessionType = isSender ? TradeSessionType.SENDING_VAULT : TradeSessionType.RECEIVING_VAULT;

        tradeService.getPlayerSessions().put(player.getUniqueId(),
                new TradeSession(sessionType, nationId,
                        trade.isSender(nationId) ? trade.getReceivingNationId() : trade.getSendingNationId(), 0));

        // Register player as viewing this trade vault for real-time updates
        plugin.getVaultUpdateManager().registerTradeVaultViewer(tradeId, isSender, player.getUniqueId());
    }

    public void updateTradeVault(Player player, TradeSession session, Inventory inventory) {
        // Create a copy of the inventory contents, ignoring buttons
        ItemStack[] contents = new ItemStack[inventory.getSize()];

        for (int i = 0; i < contents.length; i++) {
            // Skip button slots
            if (i == CONFIRM_BUTTON_SLOT || i == INFO_BUTTON_SLOT) {
                continue;
            }
            contents[i] = inventory.getItem(i);
        }

        // Save items to database
        boolean isSender = session.getType() == TradeSessionType.SENDING_VAULT;
        saveTradeVaultItems(session.getTradeId(), isSender, contents);

        // Update other viewers of this trade vault
        plugin.getVaultUpdateManager().updateTradeVaultViewers(
                session.getTradeId(),
                isSender,
                player.getUniqueId(),
                inventory);
    }

    public void handleTradeVaultClick(InventoryClickEvent event, TradeSession session) {
        // Cancel interaction with special buttons
        int slot = event.getRawSlot();
        if (slot == INFO_BUTTON_SLOT || slot == CONFIRM_BUTTON_SLOT) {
            event.setCancelled(true);

            // Handle confirm button click
            if (slot == CONFIRM_BUTTON_SLOT && event.getClick().isLeftClick()) {
                handleConfirmButtonClick((Player) event.getWhoClicked(), session);
            }
            return;
        }

        // Prevent shift-clicking items into button slots
        if (event.getClick().isShiftClick() && event.getCurrentItem() != null) {
            // Calculate where the item would go
            int targetSlot = getNextEmptySlotExcludingButtons(event.getInventory());
            if (targetSlot == INFO_BUTTON_SLOT || targetSlot == CONFIRM_BUTTON_SLOT) {
                event.setCancelled(true);
            }
        }

        // Handle updating other viewers after click completes
        // Schedule an update for the next tick to ensure inventory changes are complete
        if (!event.isCancelled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = (Player) event.getWhoClicked();
                @SuppressWarnings("unused")
                boolean isSender = session.getType() == TradeSessionType.SENDING_VAULT;

                updateTradeVault(player, session, event.getInventory());
            });
        }
    }

    private int getNextEmptySlotExcludingButtons(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != INFO_BUTTON_SLOT && i != CONFIRM_BUTTON_SLOT &&
                    inventory.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private void handleConfirmButtonClick(Player player, TradeSession session) {
        // Function to handle confirming trade settings
        player.sendMessage(plugin.getLocalizationManager().getComponent("trade.vault_contents_saved"));
    }

    private ItemStack createInfoButton(Trade trade, boolean isSender) {
        ItemStack infoButton = new ItemStack(Material.BOOK);
        ItemMeta meta = infoButton.getItemMeta();

        String role = isSender ? "Sender" : "Receiver";
        meta.displayName(net.kyori.adventure.text.Component.text("Trade Information")
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Trade ID: " + trade.getId())
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text("Your Role: " + role)
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        lore.add(net.kyori.adventure.text.Component.text("Status: " + trade.getStatus())
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));

        meta.lore(lore);
        infoButton.setItemMeta(meta);
        return infoButton;
    }

    private ItemStack createConfirmButton() {
        ItemStack confirmButton = new ItemStack(Material.EMERALD);
        ItemMeta meta = confirmButton.getItemMeta();

        meta.displayName(net.kyori.adventure.text.Component.text("Save Trade Vault")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Click to confirm and save")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));

        meta.lore(lore);
        confirmButton.setItemMeta(meta);
        return confirmButton;
    }

    // Methods to interact with the database
    public ItemStack[] getTradeVaultItems(String tradeId, boolean isSender) {
        // Check cache first
        if (tradeVaultItems.containsKey(tradeId) &&
                tradeVaultItems.get(tradeId).containsKey(isSender)) {
            return tradeVaultItems.get(tradeId).get(isSender);
        }

        // Not in cache, load from database
        return plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<ItemStack[]>() {
            @Override
            public ItemStack[] execute(Connection conn, DSLContext context) throws SQLException {
                String field = isSender ? "sender_items" : "receiver_items";

                Record record = context.select(DSL.field(field))
                        .from("trade_vaults")
                        .where(DSL.field("trade_id").eq(tradeId))
                        .fetchOne();

                if (record == null) {
                    // Create empty vault entry if it doesn't exist
                    context.insertInto(
                            DSL.table("trade_vaults"),
                            DSL.field("trade_id"),
                            DSL.field("execution_interval"))
                            .values(tradeId, 24) // Default 24 hour interval
                            .onDuplicateKeyIgnore()
                            .execute();

                    return new ItemStack[54];
                }

                String itemsJson = record.get(field, String.class);
                if (itemsJson == null || itemsJson.isEmpty()) {
                    return new ItemStack[54];
                }

                // Deserialize items
                List<Map<String, Object>> itemsList = gson.fromJson(
                        itemsJson, new TypeToken<List<Map<String, Object>>>() {
                        }.getType());

                ItemStack[] items = new ItemStack[54];
                int index = 0;
                for (Map<String, Object> itemMap : itemsList) {
                    if (index >= items.length)
                        break;

                    if (itemMap != null) {
                        try {
                            items[index] = ItemStack.deserialize(itemMap);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to deserialize trade item: " + e.getMessage());
                        }
                    }
                    index++;
                }

                // Store in cache
                tradeVaultItems.computeIfAbsent(tradeId, k -> new HashMap<>())
                        .put(isSender, items);

                return items;
            }
        });
    }

    public void saveTradeVaultItems(String tradeId, boolean isSender, ItemStack[] items) {
        // Update cache
        tradeVaultItems.computeIfAbsent(tradeId, k -> new HashMap<>())
                .put(isSender, items);

        // Convert items to JSON
        List<Map<String, Object>> serializedItems = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                try {
                    serializedItems.add(item.serialize());
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to serialize trade item: " + e.getMessage());
                }
            } else {
                serializedItems.add(null);
            }
        }

        String itemsJson = gson.toJson(serializedItems);
        String field = isSender ? "sender_items" : "receiver_items";

        // Save to database
        plugin.getDatabaseManager().executeWithLock(new DatabaseOperation<Void>() {
            @Override
            public Void execute(Connection conn, DSLContext context) throws SQLException {
                // Check if entry exists
                Record record = context.select(DSL.field("trade_id"))
                        .from("trade_vaults")
                        .where(DSL.field("trade_id").eq(tradeId))
                        .fetchOne();

                if (record == null) {
                    // Create new entry
                    context.insertInto(
                            DSL.table("trade_vaults"),
                            DSL.field("trade_id"),
                            DSL.field(field),
                            DSL.field("execution_interval"))
                            .values(tradeId, itemsJson, 24)
                            .execute();
                } else {
                    // Update existing entry
                    context.update(DSL.table("trade_vaults"))
                            .set(DSL.field(field), itemsJson)
                            .where(DSL.field("trade_id").eq(tradeId))
                            .execute();
                }
                return null;
            }
        });
    }

    // Clear cache for a specific trade
    public void clearCache(String tradeId) {
        tradeVaultItems.remove(tradeId);
    }

    // Clear all cache
    public void clearAllCache() {
        tradeVaultItems.clear();
    }

    // When a player closes the trade vault inventory
    public void handleInventoryClose(Player player, TradeSession session) {
        boolean isSender = session.getType() == TradeSessionType.SENDING_VAULT;

        // Unregister the player from vault viewers
        plugin.getVaultUpdateManager().unregisterTradeVaultViewer(
                session.getTradeId(),
                isSender,
                player.getUniqueId());
    }
}

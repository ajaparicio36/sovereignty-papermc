package com.tatayless.sovereignty.services.trade;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.Trade;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TradeUIHandler {
    private final TradeService tradeService;
    private final Sovereignty plugin;

    private static final int CONFIRM_SLOT = 8; // Confirm button in trade creation menu
    private static final int CONFIRM_DELETE_SLOT = 3; // Confirm delete button
    private static final int CANCEL_DELETE_SLOT = 5; // Cancel delete button

    public TradeUIHandler(TradeService tradeService, Sovereignty plugin) {
        this.tradeService = tradeService;
        this.plugin = plugin;
    }

    public void openTradeCreationMenu(Player player, String senderNationId, String receiverNationId, int interval) {
        Nation senderNation = tradeService.getNationService().getNation(senderNationId);
        Nation receiverNation = tradeService.getNationService().getNation(receiverNationId);

        if (senderNation == null || receiverNation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.creation-failed"));
            return;
        }

        // Create inventory for trade creation
        int rows = plugin.getConfigManager().getTradeVaultRows();
        int size = rows * 9;
        Inventory inventory = Bukkit.createInventory(null, size,
                net.kyori.adventure.text.Component.text("Create Trade: " + receiverNation.getName()));

        // Add confirm button to last slot
        inventory.setItem(CONFIRM_SLOT, createConfirmItem());

        // Open the inventory
        player.openInventory(inventory);

        // Create a session to track this trade creation
        tradeService.getPlayerSessions().put(player.getUniqueId(),
                new TradeSession(TradeSessionType.CREATE, senderNationId, receiverNationId, interval));
    }

    public void openTradeDeleteMenu(Player player, String nationId) {
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
                net.kyori.adventure.text.Component.text("Delete Trade"));

        // Populate with trade items
        for (int i = 0; i < nationTrades.size() && i < inventory.getSize(); i++) {
            Trade trade = nationTrades.get(i);
            ItemStack tradeItem = createTradeItem(trade);
            inventory.setItem(i, tradeItem);
        }

        // Open the inventory
        player.openInventory(inventory);

        // Create a session for track selection
        tradeService.getPlayerSessions().put(player.getUniqueId(),
                new TradeSession(TradeSessionType.DELETE_SELECT, nationId, null, 0));
    }

    public void openTradeConfirmDeleteMenu(Player player, String nationId, String tradeId) {
        Trade trade = tradeService.getActiveTrades().get(tradeId);
        if (trade == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.not-found"));
            return;
        }

        // Create confirm/cancel inventory
        Inventory inventory = Bukkit.createInventory(null, 9,
                net.kyori.adventure.text.Component.text("Confirm Delete"));

        // Add confirm and cancel buttons
        inventory.setItem(CONFIRM_DELETE_SLOT, createConfirmDeleteItem());
        inventory.setItem(CANCEL_DELETE_SLOT, createCancelDeleteItem());

        // Open the inventory
        player.openInventory(inventory);

        // Create a session for confirmation
        TradeSession session = new TradeSession(TradeSessionType.DELETE_CONFIRM, nationId, null, 0);
        session.setTradeId(tradeId);
        tradeService.getPlayerSessions().put(player.getUniqueId(), session);
    }

    public void openTradeListMenu(Player player, String nationId) {
        List<Trade> nationTrades = tradeService.getActiveTrades().values().stream()
                .filter(t -> t.getSendingNationId().equals(nationId) || t.getReceivingNationId().equals(nationId))
                .collect(Collectors.toList());

        if (nationTrades.isEmpty()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.no-trades"));
            return;
        }

        // Create inventory for trade list
        int rows = (int) Math.ceil(nationTrades.size() / 9.0) + 1; // +1 for margin
        rows = Math.min(6, Math.max(1, rows)); // Between 1 and 6 rows

        Inventory inventory = Bukkit.createInventory(null, rows * 9,
                net.kyori.adventure.text.Component.text("Nation Trades"));

        // Populate with trade items
        for (int i = 0; i < nationTrades.size() && i < inventory.getSize(); i++) {
            Trade trade = nationTrades.get(i);
            ItemStack tradeItem = createTradeItem(trade);
            inventory.setItem(i, tradeItem);
        }

        // Open the inventory
        player.openInventory(inventory);

        // Create a session for track selection
        tradeService.getPlayerSessions().put(player.getUniqueId(),
                new TradeSession(TradeSessionType.LIST, nationId, null, 0));
    }

    // Inventory click handlers
    public void handleTradeCreationClick(InventoryClickEvent event, TradeSession session) {
        // If clicked on confirm button
        if (event.getSlot() == CONFIRM_SLOT && event.getCurrentItem() != null &&
                event.getCurrentItem().getType() == Material.EMERALD) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();

            // Get all items in inventory
            Inventory inventory = event.getInventory();
            List<ItemStack> tradeItems = new ArrayList<>();

            for (int i = 0; i < inventory.getSize(); i++) {
                if (i != CONFIRM_SLOT && inventory.getItem(i) != null) {
                    tradeItems.add(inventory.getItem(i).clone());
                }
            }

            if (tradeItems.isEmpty()) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("trade.no-items"));
                return;
            }

            // Create the trade
            TradeItemsUtil.createTrade(plugin, tradeService, player, session.getNationId(),
                    session.getPartnerNationId(),
                    tradeItems, session.getInterval());
            player.closeInventory();
        }
    }

    public void handleTradeListClick(InventoryClickEvent event, TradeSession session) {
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

        // Open the trade vault
        player.closeInventory(); // Close current inventory first
        tradeService.openTradeVault(player, session.getNationId(), tradeId);
    }

    public void handleTradeDeleteSelectClick(InventoryClickEvent event, TradeSession session) {
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

        // Open confirmation menu
        player.closeInventory(); // Close current inventory first
        tradeService.openTradeConfirmDeleteMenu(player, session.getNationId(), tradeId);
    }

    public void handleTradeDeleteConfirmClick(InventoryClickEvent event, TradeSession session) {
        if (event.getCurrentItem() == null)
            return;

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();

        if (event.getSlot() == CONFIRM_DELETE_SLOT) {
            // Delete the trade
            tradeService.deleteTrade(player, session.getTradeId());
            player.closeInventory();
        } else if (event.getSlot() == CANCEL_DELETE_SLOT) {
            // Cancel deletion
            player.closeInventory();
        }
    }

    // UI item creation methods
    private ItemStack createConfirmItem() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Confirm Trade")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(net.kyori.adventure.text.Component.text("Click to confirm this trade")
                .color(net.kyori.adventure.text.format.NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createConfirmDeleteItem() {
        ItemStack item = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Confirm Delete")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCancelDeleteItem() {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Cancel")
                .color(net.kyori.adventure.text.format.NamedTextColor.RED));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTradeItem(Trade trade) {
        Nation sendingNation = tradeService.getNationService().getNation(trade.getSendingNationId());
        Nation receivingNation = tradeService.getNationService().getNation(trade.getReceivingNationId());
        String senderName = sendingNation != null ? sendingNation.getName() : "Unknown";
        String receiverName = receivingNation != null ? receivingNation.getName() : "Unknown";

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(senderName + " → " + receiverName)
                .color(net.kyori.adventure.text.format.NamedTextColor.GOLD));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        net.kyori.adventure.text.format.TextColor statusColor;
        switch (trade.getStatus()) {
            case ACTIVE:
                statusColor = net.kyori.adventure.text.format.NamedTextColor.GREEN;
                break;
            case PENDING:
                statusColor = net.kyori.adventure.text.format.NamedTextColor.YELLOW;
                break;
            case CANCELLED:
                statusColor = net.kyori.adventure.text.format.NamedTextColor.RED;
                break;
            case COMPLETED:
                statusColor = net.kyori.adventure.text.format.NamedTextColor.AQUA;
                break;
            default:
                statusColor = net.kyori.adventure.text.format.NamedTextColor.GRAY;
        }

        lore.add(net.kyori.adventure.text.Component.text("Status: " + trade.getStatus().toString())
                .color(statusColor));
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
}

package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.commands.nation.NationCommandExecutor.SubCommand;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.TradeService;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TradeCommand implements SubCommand {
    private final Sovereignty plugin;
    private final TradeService tradeService;

    public TradeCommand(Sovereignty plugin) {
        this.plugin = plugin;
        this.tradeService = plugin.getServiceManager().getTradeService();
    }

    @Override
    public String getName() {
        return "trade";
    }

    @Override
    public String getDescription() {
        return "Manage nation trades";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        String playerId = player.getUniqueId().toString();
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService().getPlayer(playerId);

        // Check if player is in a nation
        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.not-in-nation"));
            return true;
        }

        String nationId = sovereigntyPlayer.getNationId();
        Nation nation = plugin.getServiceManager().getNationService().getNation(nationId);

        // Check if player is an officer (president or senator)
        if (!nation.isOfficer(playerId)) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("nation.no-permission"));
            return true;
        }

        // No arguments, show help
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        // Handle subcommands
        String subCmd = args[0].toLowerCase();
        switch (subCmd) {
            case "create":
                return handleCreate(player, nation, Arrays.copyOfRange(args, 1, args.length));
            case "list":
                return handleList(player, nation);
            case "delete":
                return handleDelete(player, nation);
            case "npc":
                return handleNPC(player, nation, Arrays.copyOfRange(args, 1, args.length));
            default:
                showHelp(player);
                return true;
        }
    }

    private boolean handleCreate(Player player, Nation nation, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.specify-target-nation"));
            return true;
        }

        // Find target nation by name
        String targetNationName = args[0];
        Nation targetNation = plugin.getServiceManager().getNationService().getNationByName(targetNationName);

        if (targetNation == null) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.nation-not-found", 
                    "nation", targetNationName));
            return true;
        }

        // Can't trade with your own nation
        if (targetNation.getId().equals(nation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.cannot-trade-self"));
            return true;
        }

        // Parse interval if provided
        int interval = plugin.getConfigManager().getDefaultTradeInterval();
        if (args.length > 1) {
            try {
                interval = Integer.parseInt(args[1]);
                int maxInterval = plugin.getConfigManager().getMaxTradeInterval();
                if (interval < 1) {
                    interval = 1;
                } else if (interval > maxInterval) {
                    interval = maxInterval;
                    player.sendMessage(plugin.getLocalizationManager().getComponent("trade.interval-capped", 
                            "max", String.valueOf(maxInterval)));
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLocalizationManager().getComponent("trade.invalid-interval"));
            }
        }

        // Open trade creation UI
        tradeService.openTradeCreationMenu(player, nation.getId(), targetNation.getId(), interval);
        return true;
    }

    private boolean handleList(Player player, Nation nation) {
        tradeService.openTradeListMenu(player, nation.getId());
        return true;
    }

    private boolean handleDelete(Player player, Nation nation) {
        tradeService.openTradeDeleteMenu(player, nation.getId());
        return true;
    }

    private boolean handleNPC(Player player, Nation nation, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-specify-action"));
            return true;
        }

        String npcAction = args[0].toLowerCase();
        switch (npcAction) {
            case "create":
                tradeService.openTradeNPCSelectionMenu(player, nation.getId());
                return true;
            case "delete":
                tradeService.attemptDeleteTradeNPC(player, nation.getId());
                return true;
            default:
                player.sendMessage(plugin.getLocalizationManager().getComponent("trade.npc-invalid-action"));
                return true;
        }
    }

    private void showHelp(Player player) {
        player.sendMessage(plugin.getLocalizationManager().getComponent("help.trade-header"));
        player.sendMessage(plugin.getLocalizationManager().getComponent("help.trade-create"));
        player.sendMessage(plugin.getLocalizationManager().getComponent("help.trade-list"));
        player.sendMessage(plugin.getLocalizationManager().getComponent("help.trade-delete"));
        player.sendMessage(plugin.getLocalizationManager().getComponent("help.trade-npc-create"));
        player.sendMessage(plugin.getLocalizationManager().getComponent("help.trade-npc-delete"));
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "list", "delete", "npc").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            return Arrays.asList("create", "delete").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            // Tab complete with nation names for the target nation
            return plugin.getServiceManager().getNationService().getAllNations().stream()
                    .map(Nation::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

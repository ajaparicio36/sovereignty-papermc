package com.tatayless.sovereignty.commands.war;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import com.tatayless.sovereignty.services.NationService;
import com.tatayless.sovereignty.services.PlayerService;
import com.tatayless.sovereignty.services.WarService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WarCommand implements CommandExecutor, TabCompleter {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final PlayerService playerService;
    private final WarService warService;

    public WarCommand(Sovereignty plugin) {
        this.plugin = plugin;
        this.nationService = plugin.getServiceManager().getNationService();
        this.playerService = plugin.getServiceManager().getPlayerService();
        this.warService = plugin.getServiceManager().getWarService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLocalizationManager().getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;
        String playerId = player.getUniqueId().toString();

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "declare":
                return handleDeclareWar(player, playerId, args);
            case "list":
                return handleListWars(player, playerId, args);
            case "info":
                return handleWarInfo(player, args);
            case "cancel":
                return handleCancelWar(player, playerId, args);
            default:
                showHelp(player);
                return true;
        }
    }

    private boolean handleDeclareWar(Player player, String playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/war declare <nation>"));
            return true;
        }

        String targetNationName = args[1];
        SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(playerId);

        if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        Nation playerNation = nationService.getNation(sovereigntyPlayer.getNationId());

        if (playerNation == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        // Only president can declare war
        if (!playerNation.getPresidentId().equals(playerId)) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-president"));
            return true;
        }

        // Find target nation
        Nation targetNation = nationService.getNationByName(targetNationName);
        if (targetNation == null) {
            player.sendMessage("§cNation '" + targetNationName + "' not found.");
            return true;
        }

        // Check if already at war
        if (warService.isAtWar(playerNation.getId(), targetNation.getId())) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("war.already-at-war"));
            return true;
        }

        // Declare war
        warService.declareWar(playerNation.getId(), targetNation.getId()).thenAccept(war -> {
            if (war != null) {
                // Success message handled by war service
            } else {
                player.sendMessage("§cFailed to declare war.");
            }
        });

        return true;
    }

    private boolean handleListWars(Player player, String playerId, String[] args) {
        List<WarService.War> wars;

        if (args.length > 1 && args[1].equalsIgnoreCase("all") && player.hasPermission("sovereignty.admin.wars")) {
            // Admin viewing all wars
            wars = warService.getActiveWars();
            player.sendMessage("§6--- All Active Wars ---");
        } else {
            // Player viewing their nation's wars
            SovereigntyPlayer sovereigntyPlayer = playerService.getPlayer(playerId);

            if (sovereigntyPlayer == null || !sovereigntyPlayer.hasNation()) {
                player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
                return true;
            }

            wars = warService.getNationWars(sovereigntyPlayer.getNationId());
            player.sendMessage("§6--- Your Nation's Wars ---");
        }

        if (wars.isEmpty()) {
            player.sendMessage("§7No active wars.");
            return true;
        }

        for (WarService.War war : wars) {
            Nation attacker = nationService.getNation(war.getAttackerNationId());
            Nation defender = nationService.getNation(war.getDefenderNationId());

            if (attacker != null && defender != null) {
                player.sendMessage(String.format("§f%s §7vs §f%s §7- Kills: §c%d§7/§a%d §7(Required: §6%d§7)",
                        attacker.getName(), defender.getName(),
                        war.getAttackerKills(), war.getDefenderKills(),
                        war.getRequiredKills()));
            }
        }

        return true;
    }

    private boolean handleWarInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/war info <nation1> <nation2>"));
            return true;
        }

        String nation1Name = args[1];
        String nation2Name = args.length > 2 ? args[2] : null;

        Nation nation1 = nationService.getNationByName(nation1Name);

        if (nation1 == null) {
            player.sendMessage("§cNation '" + nation1Name + "' not found.");
            return true;
        }

        if (nation2Name == null) {
            // Show all wars for nation1
            List<WarService.War> wars = warService.getNationWars(nation1.getId());

            if (wars.isEmpty()) {
                player.sendMessage("§7Nation '" + nation1.getName() + "' is not involved in any wars.");
                return true;
            }

            player.sendMessage("§6--- Wars involving " + nation1.getName() + " ---");

            for (WarService.War war : wars) {
                Nation attacker = nationService.getNation(war.getAttackerNationId());
                Nation defender = nationService.getNation(war.getDefenderNationId());

                if (attacker != null && defender != null) {
                    player.sendMessage(String.format("§f%s §7vs §f%s §7- Kills: §c%d§7/§a%d §7(Required: §6%d§7)",
                            attacker.getName(), defender.getName(),
                            war.getAttackerKills(), war.getDefenderKills(),
                            war.getRequiredKills()));
                }
            }
        } else {
            // Show specific war between two nations
            Nation nation2 = nationService.getNationByName(nation2Name);

            if (nation2 == null) {
                player.sendMessage("§cNation '" + nation2Name + "' not found.");
                return true;
            }

            WarService.War war = warService.getWarBetweenNations(nation1.getId(), nation2.getId());

            if (war == null) {
                player.sendMessage("§7There is no active war between " + nation1.getName() +
                        " and " + nation2.getName() + ".");
                return true;
            }

            Nation attacker = nationService.getNation(war.getAttackerNationId());
            Nation defender = nationService.getNation(war.getDefenderNationId());

            player.sendMessage("§6--- War Details ---");
            player.sendMessage("§fAttacker: §a" + attacker.getName());
            player.sendMessage("§fDefender: §c" + defender.getName());
            player.sendMessage("§fAttacker Kills: §a" + war.getAttackerKills());
            player.sendMessage("§fDefender Kills: §c" + war.getDefenderKills());
            player.sendMessage("§fKills Required: §6" + war.getRequiredKills());
        }

        return true;
    }

    private boolean handleCancelWar(Player player, String playerId, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/war cancel <id or nation1 nation2>"));
            return true;
        }

        // Check if player has admin permission
        if (!player.hasPermission("sovereignty.admin.wars")) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("general.no-permission"));
            return true;
        }

        String warId = null;

        // Handle different types of arguments - either direct war ID or nation names
        if (args.length == 2) {
            // Try to use the argument as a direct war ID
            warId = args[1];
        } else if (args.length >= 3) {
            // Try to find the war between two nations
            String nation1Name = args[1];
            String nation2Name = args[2];

            Nation nation1 = nationService.getNationByName(nation1Name);
            Nation nation2 = nationService.getNationByName(nation2Name);

            if (nation1 == null) {
                player.sendMessage("§cNation '" + nation1Name + "' not found.");
                return true;
            }

            if (nation2 == null) {
                player.sendMessage("§cNation '" + nation2Name + "' not found.");
                return true;
            }

            WarService.War war = warService.getWarBetweenNations(nation1.getId(), nation2.getId());

            if (war == null) {
                player.sendMessage("§7There is no active war between " + nation1.getName() +
                        " and " + nation2.getName() + ".");
                return true;
            }

            warId = war.getId();
        }

        final String finalWarId = warId;

        // Cancel the war
        warService.cancelWar(finalWarId).thenAccept(success -> {
            if (success) {
                player.sendMessage(plugin.getLocalizationManager().getMessage("war.cancelled"));
            } else {
                player.sendMessage("§cFailed to cancel war. Make sure the war exists and is active.");
            }
        });

        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6--- War Commands ---");
        player.sendMessage("§f/war declare <nation> §7- Declare war on another nation");
        player.sendMessage("§f/war list [all] §7- List your nation's wars or all wars (admin)");
        player.sendMessage("§f/war info <nation1> [nation2] §7- Show info about wars");

        if (player.hasPermission("sovereignty.admin.wars")) {
            player.sendMessage("§f/war cancel <nation1> <nation2> §7- Cancel a war (admin)");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("declare", "list", "info"));
            if (player.hasPermission("sovereignty.admin.wars")) {
                subCommands.add("cancel");
            }

            return subCommands.stream()
                    .filter(subCmd -> subCmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Tab complete for nation names
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("declare") || subCommand.equals("info") || subCommand.equals("cancel")) {
                return nationService.getAllNations().stream()
                        .map(Nation::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (subCommand.equals("list") && player.hasPermission("sovereignty.admin.wars")) {
                return Arrays.asList("all").stream()
                        .filter(option -> option.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();

            if ((subCommand.equals("info") || subCommand.equals("cancel"))) {
                return nationService.getAllNations().stream()
                        .map(Nation::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}

package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.managers.ToggleManager;
import com.tatayless.sovereignty.services.NationService;
import com.tatayless.sovereignty.services.PlayerService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NationCommandExecutor implements CommandExecutor, TabCompleter {
    private final Sovereignty plugin;
    @SuppressWarnings("unused")
    private final NationService nationService;
    @SuppressWarnings("unused")
    private final PlayerService playerService;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public NationCommandExecutor(Sovereignty plugin) {
        this.plugin = plugin;
        this.nationService = plugin.getServiceManager().getNationService();
        this.playerService = plugin.getServiceManager().getPlayerService();

        // Get access to toggleManager directly from CommandManager
        ToggleManager toggleManager = plugin.getCommandManager().getToggleManager();

        // Register subcommands
        registerSubCommand(new CreateCommand(plugin));
        registerSubCommand(new DisbandCommand(plugin));
        registerSubCommand(new InfoCommand(plugin));
        this.registerSubCommand(new ClaimCommand(plugin, toggleManager));
        this.registerSubCommand(new UnclaimCommand(plugin, toggleManager));
        registerSubCommand(new InviteCommand(plugin));
        registerSubCommand(new JoinCommand(plugin));
        registerSubCommand(new LeaveCommand(plugin));
        registerSubCommand(new AppointCommand(plugin));
        registerSubCommand(new AllianceCommand(plugin)); // Register the alliance command
    }

    private void registerSubCommand(SubCommand command) {
        subCommands.put(command.getName(), command);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getLocalizationManager().getMessage("general.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            // No arguments, show help
            showHelp(player);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            // Unknown subcommand, show help
            showHelp(player);
            return true;
        }

        // Execute subcommand
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.execute(player, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Tab completing the subcommand
            return subCommands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            // Tab completing arguments for a subcommand
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                return subCommand.tabComplete((Player) sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }

        return Collections.emptyList();
    }

    private void showHelp(Player player) {
        player.sendMessage(plugin.getLocalizationManager().getMessage("help.nation-header"));

        for (SubCommand subCommand : subCommands.values()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "help.nation-command",
                    "command", plugin.getCommand("nation").getLabel(),
                    "subcommand", subCommand.getName(),
                    "description", subCommand.getDescription()));
        }
    }

    // Added getter for subcommands to allow access by other classes
    public Collection<SubCommand> getSubCommands() {
        return subCommands.values();
    }

    public interface SubCommand {
        String getName();

        String getDescription();

        boolean execute(Player player, String[] args);

        List<String> tabComplete(Player player, String[] args);
    }
}

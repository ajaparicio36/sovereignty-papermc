package com.tatayless.sovereignty.commands.admin;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class AdminCommandExecutor implements CommandExecutor, TabCompleter {
    private final Sovereignty plugin;
    private final Map<String, CommandExecutor> subCommands = new HashMap<>();
    private final Map<String, TabCompleter> tabCompleters = new HashMap<>();

    public AdminCommandExecutor(Sovereignty plugin) {
        this.plugin = plugin;

        // Register subcommands
        SetPowerCommand setPowerCommand = new SetPowerCommand(plugin);
        registerSubCommand("setpower", setPowerCommand, setPowerCommand);
    }

    private void registerSubCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        subCommands.put(name.toLowerCase(), executor);
        if (tabCompleter != null) {
            tabCompleters.put(name.toLowerCase(), tabCompleter);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        CommandExecutor subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            showHelp(sender);
            return true;
        }

        // Execute subcommand
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.onCommand(sender, command, label, subArgs);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return subCommands.keySet().stream()
                    .filter(name -> name.startsWith(input) &&
                            sender.hasPermission("sovereignty.admin." + name))
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            String subCommandName = args[0].toLowerCase();
            TabCompleter tabCompleter = tabCompleters.get(subCommandName);
            if (tabCompleter != null) {
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return tabCompleter.onTabComplete(sender, command, alias, subArgs);
            }
        }

        return Collections.emptyList();
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(plugin.getLocalizationManager().getComponent("help.admin-header"));

        for (String cmdName : subCommands.keySet()) {
            if (sender.hasPermission("sovereignty.admin." + cmdName)) {
                sender.sendMessage(plugin.getLocalizationManager().getComponent(
                        "help.admin-command",
                        "command", "admin",
                        "subcommand", cmdName,
                        "description", "Administrative command"));
            }
        }
    }
}

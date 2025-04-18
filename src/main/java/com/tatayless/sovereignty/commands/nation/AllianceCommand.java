package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AllianceCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;
    private final List<NationCommandExecutor.SubCommand> subcommands = new ArrayList<>();

    public AllianceCommand(Sovereignty plugin) {
        this.plugin = plugin;

        // Register alliance subcommands
        subcommands.add(new AllianceListCommand(plugin));
        subcommands.add(new AllianceProposeCommand(plugin));
        subcommands.add(new AllianceAcceptCommand(plugin));
        subcommands.add(new AllianceDenyCommand(plugin));
        subcommands.add(new AllianceBreakCommand(plugin));
    }

    @Override
    public String getName() {
        return "alliance";
    }

    @Override
    public String getDescription() {
        return plugin.getLocalizationManager().getMessage("alliance.description", "Manage nation alliances");
    }

    @Override
    public boolean execute(Player player, String[] args) {
        SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService()
                .getPlayer(player.getUniqueId().toString());

        if (sovereigntyPlayer == null || sovereigntyPlayer.getNationId() == null) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.not-in-nation"));
            return true;
        }

        if (args.length < 1) {
            // Display help message
            player.sendMessage(
                    plugin.getLocalizationManager().getMessage("alliance.help-header"));
            for (NationCommandExecutor.SubCommand subcommand : subcommands) {
                player.sendMessage(
                        plugin.getLocalizationManager().getMessage("help.nation-command",
                                "command", "nation alliance",
                                "subcommand", subcommand.getName(),
                                "description", subcommand.getDescription()));
            }
            return true;
        }

        String subcommandName = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        for (NationCommandExecutor.SubCommand subcommand : subcommands) {
            if (subcommand.getName().equalsIgnoreCase(subcommandName)) {
                return subcommand.execute(player, subArgs);
            }
        }

        player.sendMessage(plugin.getLocalizationManager().getMessage("alliance.unknown-subcommand"));
        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        if (args.length == 1) {
            String partialSubcommand = args[0].toLowerCase();
            return subcommands.stream()
                    .map(NationCommandExecutor.SubCommand::getName)
                    .filter(name -> name.toLowerCase().startsWith(partialSubcommand))
                    .collect(Collectors.toList());
        } else if (args.length > 1) {
            String subcommandName = args[0].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            for (NationCommandExecutor.SubCommand subcommand : subcommands) {
                if (subcommand.getName().equalsIgnoreCase(subcommandName)) {
                    return subcommand.tabComplete(player, subArgs);
                }
            }
        }

        return new ArrayList<>();
    }
}

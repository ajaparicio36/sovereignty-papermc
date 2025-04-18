package com.tatayless.sovereignty.commands.nation;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class CreateCommand implements NationCommandExecutor.SubCommand {
    private final Sovereignty plugin;
    private final Pattern namePattern = Pattern.compile("^[a-zA-Z0-9_ -]{3,32}$");

    public CreateCommand(Sovereignty plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public String getDescription() {
        return "Create a new nation";
    }

    @Override
    public boolean execute(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plugin.getLocalizationManager().getMessage(
                    "general.invalid-args",
                    "usage", "/nation create <name>"));
            return false;
        }

        String nationName = args[0];

        // Validate nation name
        if (!namePattern.matcher(nationName).matches()) {
            player.sendMessage(
                    "§cInvalid nation name. Names must be 3-32 characters and may only contain letters, numbers, spaces, underscores and hyphens.");
            return false;
        }

        // Check if player is already in a nation
        if (plugin.getServiceManager().getPlayerService().getPlayer(player.getUniqueId().toString()).hasNation()) {
            player.sendMessage(plugin.getLocalizationManager().getMessage("nation.already-in-nation"));
            return true;
        }

        // Create nation
        plugin.getServiceManager().getNationService().createNation(nationName, player).thenAccept(nation -> {
            if (nation != null) {
                player.sendMessage(plugin.getLocalizationManager().getMessage(
                        "nation.created",
                        "name", nation.getName()));
            } else {
                player.sendMessage(plugin.getLocalizationManager().getMessage("nation.already-exists"));
            }
        });

        return true;
    }

    @Override
    public List<String> tabComplete(Player player, String[] args) {
        // No tab completion for nation creation
        return Collections.emptyList();
    }
}

package com.tatayless.sovereignty.commands.admin;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.services.AdminService;
import com.tatayless.sovereignty.services.NationService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SetPowerCommand implements CommandExecutor, TabCompleter {

    private final Sovereignty plugin;
    private final NationService nationService;
    private final AdminService adminService;

    public SetPowerCommand(Sovereignty plugin) {
        this.plugin = plugin;
        this.nationService = plugin.getServiceManager().getNationService();
        this.adminService = plugin.getServiceManager().getAdminService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sovereignty.admin.setpower")) {
            sender.sendMessage(plugin.getLocalizationManager().getComponent("general.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getLocalizationManager().getComponent("admin.setpower.usage"));
            return true;
        }

        String nationName = args[0];
        double power;

        try {
            power = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getLocalizationManager().getComponent("admin.setpower.invalid-power"));
            return true;
        }

        adminService.setNationPowerByName(nationName, power).thenAcceptAsync(success -> {
            if (success) {
                sender.sendMessage(plugin.getLocalizationManager().getComponent("admin.setpower.success",
                        "nation", nationName,
                        "power", String.valueOf(power)));
            } else {
                sender.sendMessage(plugin.getLocalizationManager().getComponent("admin.setpower.failed"));
            }
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sovereignty.admin.setpower")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return nationService.getAllNations().stream()
                    .map(Nation::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            if ("".equals(input)) {
                suggestions.add("1.0");
                suggestions.add("5.0");
                suggestions.add("10.0");
                suggestions.add("25.0");
                suggestions.add("50.0");
                suggestions.add("100.0");
            }
            return suggestions.stream()
                    .filter(s -> s.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}

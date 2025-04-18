package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;
import com.tatayless.sovereignty.models.SovereigntyPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AllianceService {
    private final Sovereignty plugin;
    private final NationService nationService;
    private final Map<String, Set<String>> allianceRequests = new HashMap<>();

    public AllianceService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
    }

    public void loadAlliances() {
        // No need to load from database, alliances are stored directly in nation
        // objects
        plugin.getLogger().info("Alliance Service initialized");
    }

    public CompletableFuture<Boolean> proposeAlliance(String senderNationId, String receiverNationId, String playerId) {
        Nation senderNation = nationService.getNation(senderNationId);
        Nation receiverNation = nationService.getNation(receiverNationId);

        if (senderNation == null || receiverNation == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has permission (president or senator)
        if (!senderNation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if nations are already allied
        if (senderNation.getAlliances().contains(receiverNationId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Store alliance request
        allianceRequests.computeIfAbsent(receiverNationId, k -> new HashSet<>()).add(senderNationId);

        // Notify players
        notifyNationOfficers(receiverNationId, plugin.getLocalizationManager().getMessage(
                "alliance.request-received",
                "nation", senderNation.getName()));

        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> acceptAlliance(String nationId, String senderNationId, String playerId) {
        Nation nation = nationService.getNation(nationId);
        Nation senderNation = nationService.getNation(senderNationId);

        if (nation == null || senderNation == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has permission (president or senator)
        if (!nation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if the request exists
        Set<String> requests = allianceRequests.getOrDefault(nationId, new HashSet<>());
        if (!requests.contains(senderNationId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Form alliance
        nation.addAlliance(senderNationId);
        senderNation.addAlliance(nationId);

        // Remove the request
        requests.remove(senderNationId);
        if (requests.isEmpty()) {
            allianceRequests.remove(nationId);
        } else {
            allianceRequests.put(nationId, requests);
        }

        // Save nations
        CompletableFuture<Boolean> future1 = nationService.saveNation(nation);
        CompletableFuture<Boolean> future2 = nationService.saveNation(senderNation);

        // Notify players
        String message = plugin.getLocalizationManager().getMessage(
                "alliance.formed",
                "nation", senderNation.getName());
        notifyNationPlayers(nationId, message);

        message = plugin.getLocalizationManager().getMessage(
                "alliance.formed",
                "nation", nation.getName());
        notifyNationPlayers(senderNationId, message);

        return CompletableFuture.allOf(future1, future2)
                .thenApply(v -> future1.join() && future2.join());
    }

    public CompletableFuture<Boolean> breakAlliance(String nationId, String allyNationId, String playerId) {
        Nation nation = nationService.getNation(nationId);
        Nation allyNation = nationService.getNation(allyNationId);

        if (nation == null || allyNation == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if player has permission (president or senator)
        if (!nation.isOfficer(playerId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Check if alliance exists
        if (!nation.getAlliances().contains(allyNationId)) {
            return CompletableFuture.completedFuture(false);
        }

        // Break alliance
        nation.removeAlliance(allyNationId);
        allyNation.removeAlliance(nationId);

        // Save nations
        CompletableFuture<Boolean> future1 = nationService.saveNation(nation);
        CompletableFuture<Boolean> future2 = nationService.saveNation(allyNation);

        // Notify players
        String message = plugin.getLocalizationManager().getMessage(
                "alliance.broken",
                "nation", allyNation.getName());
        notifyNationPlayers(nationId, message);

        message = plugin.getLocalizationManager().getMessage(
                "alliance.broken",
                "nation", nation.getName());
        notifyNationPlayers(allyNationId, message);

        return CompletableFuture.allOf(future1, future2)
                .thenApply(v -> future1.join() && future2.join());
    }

    public boolean isAllied(String nationId1, String nationId2) {
        Nation nation1 = nationService.getNation(nationId1);
        if (nation1 == null) {
            return false;
        }
        return nation1.getAlliances().contains(nationId2);
    }

    public Set<String> getAllianceRequests(String nationId) {
        return allianceRequests.getOrDefault(nationId, new HashSet<>());
    }

    private void notifyNationOfficers(String nationId, String message) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null)
            return;

        // Notify president
        Player president = Bukkit.getPlayer(UUID.fromString(nation.getPresidentId()));
        if (president != null && president.isOnline()) {
            president.sendMessage(message);
        }

        // Notify senators
        for (String senatorId : nation.getSenators()) {
            Player senator = Bukkit.getPlayer(UUID.fromString(senatorId));
            if (senator != null && senator.isOnline()) {
                senator.sendMessage(message);
            }
        }
    }

    private void notifyNationPlayers(String nationId, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            SovereigntyPlayer sovereigntyPlayer = plugin.getServiceManager().getPlayerService()
                    .getPlayer(player.getUniqueId().toString());
            if (sovereigntyPlayer != null && nationId.equals(sovereigntyPlayer.getNationId())) {
                player.sendMessage(message);
            }
        }
    }
}

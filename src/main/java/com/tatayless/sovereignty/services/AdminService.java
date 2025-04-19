package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.models.Nation;

import java.util.concurrent.CompletableFuture;

/**
 * Service for handling administrative functions
 */
public class AdminService {

    @SuppressWarnings("unused")
    private final Sovereignty plugin;
    private final NationService nationService;

    public AdminService(Sovereignty plugin, NationService nationService) {
        this.plugin = plugin;
        this.nationService = nationService;
    }

    /**
     * Set power for a nation
     *
     * @param nationId The ID of the nation
     * @param power    The new power value
     * @return CompletableFuture with result of the operation
     */
    public CompletableFuture<Boolean> setNationPower(String nationId, double power) {
        Nation nation = nationService.getNation(nationId);
        if (nation == null) {
            return CompletableFuture.completedFuture(false);
        }

        nation.setPower(power);
        return nationService.saveNation(nation);
    }

    /**
     * Set power for a nation by name
     *
     * @param nationName The name of the nation
     * @param power      The new power value
     * @return CompletableFuture with result of the operation
     */
    public CompletableFuture<Boolean> setNationPowerByName(String nationName, double power) {
        Nation nation = nationService.getNationByName(nationName);
        if (nation == null) {
            return CompletableFuture.completedFuture(false);
        }

        nation.setPower(power);
        return nationService.saveNation(nation);
    }
}

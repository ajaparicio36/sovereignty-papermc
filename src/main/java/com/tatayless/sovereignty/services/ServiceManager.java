package com.tatayless.sovereignty.services;

import com.tatayless.sovereignty.Sovereignty;

public class ServiceManager {
    private final Sovereignty plugin;

    private NationService nationService;
    private PlayerService playerService;
    private ChunkService chunkService;
    private WarService warService;
    private AllianceService allianceService;
    private TradeService tradeService;
    private VaultService vaultService;
    private PowerService powerService;

    public ServiceManager(Sovereignty plugin) {
        this.plugin = plugin;
    }

    public void initializeServices() {
        // Initialize services in correct order (dependency order)
        playerService = new PlayerService(plugin);
        nationService = new NationService(plugin, playerService);
        chunkService = new ChunkService(plugin, nationService);
        warService = new WarService(plugin, nationService, playerService);
        allianceService = new AllianceService(plugin, nationService);
        vaultService = new VaultService(plugin, nationService);
        tradeService = new TradeService(plugin, nationService, vaultService);
        powerService = new PowerService(plugin, nationService, playerService, allianceService, tradeService,
                warService);

        // Load data
        playerService.loadPlayers();
        nationService.loadNations();
        warService.loadWars();
        allianceService.loadAlliances();
        tradeService.loadTrades();
        vaultService.loadVaults();

        // Start tasks
        powerService.startRecalculationTask();
        tradeService.startTradeExecutionTask();
    }

    public NationService getNationService() {
        return nationService;
    }

    public PlayerService getPlayerService() {
        return playerService;
    }

    public ChunkService getChunkService() {
        return chunkService;
    }

    public WarService getWarService() {
        return warService;
    }

    public AllianceService getAllianceService() {
        return allianceService;
    }

    public TradeService getTradeService() {
        return tradeService;
    }

    public VaultService getVaultService() {
        return vaultService;
    }

    public PowerService getPowerService() {
        return powerService;
    }
}

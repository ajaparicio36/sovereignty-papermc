package com.tatayless.sovereignty.database;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.scheduler.BukkitRunnable;
import org.jooq.DSLContext;

import java.util.concurrent.CompletableFuture;

public class TableManager {
    private final Sovereignty plugin;
    private final boolean isMySQL;

    public TableManager(Sovereignty plugin, boolean isMySQL) {
        this.plugin = plugin;
        this.isMySQL = isMySQL;
    }

    public CompletableFuture<Void> createTables(DSLContext context) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (isMySQL) {
                        createMySQLTables(context);
                    } else {
                        createSQLiteTables(context);
                    }
                    plugin.getLogger().info("Database tables have been initialized");
                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    private void createMySQLTables(DSLContext context) {
        // Nations table
        context.execute("CREATE TABLE IF NOT EXISTS nations (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(64) NOT NULL UNIQUE, " +
                "power INT NOT NULL DEFAULT 0, " +
                "claimed_chunks JSON, " +
                "alliances JSON, " +
                "president_id VARCHAR(36), " + // Store president reference directly
                "senators JSON, " + // Store senators as JSON array of player IDs
                "citizens JSON, " + // Added citizens as JSON array of player IDs
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")");

        // Players table
        context.execute("CREATE TABLE IF NOT EXISTS players (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(36) NOT NULL UNIQUE, " +
                "nation_id VARCHAR(36), " +
                "role VARCHAR(20), " + // Add role field (e.g., 'president', 'senator', 'citizen')
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE SET NULL" +
                ")");

        // Nation Vault table
        context.execute("CREATE TABLE IF NOT EXISTS nation_vaults (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "nation_id VARCHAR(36) NOT NULL UNIQUE, " +
                "items JSON, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        // Trades table
        context.execute("CREATE TABLE IF NOT EXISTS trades (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "sending_nation_id VARCHAR(36) NOT NULL, " +
                "receiving_nation_id VARCHAR(36) NOT NULL, " +
                "sending_items JSON, " +
                "receiving_items JSON, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'pending', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (sending_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (receiving_nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        // Trade Vaults table
        context.execute("CREATE TABLE IF NOT EXISTS trade_vaults (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "trade_id VARCHAR(36) NOT NULL UNIQUE, " +
                "sending_items_vault JSON, " +
                "receiving_items_vault JSON, " +
                "execution_interval INT NOT NULL DEFAULT 3, " + // Interval in minecraft days
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE" +
                ")");

        // Vault NPC table - NPCs for accessing nation vaults
        context.execute("CREATE TABLE IF NOT EXISTS vault_npcs (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "nation_id VARCHAR(36) NOT NULL UNIQUE, " +
                "nation_vault_id VARCHAR(36) NOT NULL UNIQUE, " +
                "coordinates VARCHAR(255) NOT NULL, " + // Store as "x,y,z,world"
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_vault_id) REFERENCES nation_vaults(id) ON DELETE CASCADE" +
                ")");

        // Trade Vault NPC table - NPCs for accessing trade vaults
        context.execute("CREATE TABLE IF NOT EXISTS trade_vault_npcs (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "trade_id VARCHAR(36) NOT NULL UNIQUE, " +
                "nation_id VARCHAR(36) NOT NULL, " +
                "coordinates VARCHAR(255) NOT NULL, " + // Store as "x,y,z,world"
                "is_for_sender BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");
    }

    private void createSQLiteTables(DSLContext context) {
        // Nations table
        context.execute("CREATE TABLE IF NOT EXISTS nations (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL UNIQUE, " +
                "power INTEGER NOT NULL DEFAULT 0, " +
                "claimed_chunks TEXT, " + // JSON array stored as TEXT in SQLite
                "alliances TEXT, " + // JSON array stored as TEXT in SQLite
                "president_id TEXT, " + // Store president reference directly
                "senators TEXT, " + // Store senators as JSON array of player IDs
                "citizens TEXT, " + // Added citizens as JSON array of player IDs
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now'))" +
                ")");

        // Players table
        context.execute("CREATE TABLE IF NOT EXISTS players (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL UNIQUE, " +
                "nation_id TEXT, " +
                "role TEXT, " + // Add role field (e.g., 'president', 'senator', 'citizen')
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE SET NULL" +
                ")");

        // Nation Vault table
        context.execute("CREATE TABLE IF NOT EXISTS nation_vaults (" +
                "id TEXT PRIMARY KEY, " +
                "nation_id TEXT NOT NULL UNIQUE, " +
                "items TEXT, " + // JSON as TEXT in SQLite
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        // Create trigger for updated_at on nations
        context.execute("CREATE TRIGGER IF NOT EXISTS update_nations_timestamp " +
                "AFTER UPDATE ON nations " +
                "BEGIN " +
                "   UPDATE nations SET updated_at = datetime('now') WHERE id = NEW.id; " +
                "END");

        // Trades table
        context.execute("CREATE TABLE IF NOT EXISTS trades (" +
                "id TEXT PRIMARY KEY, " +
                "sending_nation_id TEXT NOT NULL, " +
                "receiving_nation_id TEXT NOT NULL, " +
                "sending_items TEXT, " + // JSON as TEXT in SQLite
                "receiving_items TEXT, " + // JSON as TEXT in SQLite
                "status TEXT NOT NULL DEFAULT 'pending', " +
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (sending_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (receiving_nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        // Trade Vaults table
        context.execute("CREATE TABLE IF NOT EXISTS trade_vaults (" +
                "id TEXT PRIMARY KEY, " +
                "trade_id TEXT NOT NULL UNIQUE, " +
                "sending_items_vault TEXT, " + // JSON as TEXT in SQLite
                "receiving_items_vault TEXT, " + // JSON as TEXT in SQLite
                "execution_interval INTEGER NOT NULL DEFAULT 3, " + // Interval in minecraft days
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE" +
                ")");

        // Vault NPC table - NPCs for accessing nation vaults
        context.execute("CREATE TABLE IF NOT EXISTS vault_npcs (" +
                "id TEXT PRIMARY KEY, " +
                "nation_id TEXT NOT NULL UNIQUE, " +
                "nation_vault_id TEXT NOT NULL UNIQUE, " +
                "coordinates TEXT NOT NULL, " + // Store as "x,y,z,world"
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_vault_id) REFERENCES nation_vaults(id) ON DELETE CASCADE" +
                ")");

        // Trade Vault NPC table - NPCs for accessing trade vaults
        context.execute("CREATE TABLE IF NOT EXISTS trade_vault_npcs (" +
                "id TEXT PRIMARY KEY, " +
                "trade_id TEXT NOT NULL UNIQUE, " +
                "nation_id TEXT NOT NULL, " +
                "coordinates TEXT NOT NULL, " + // Store as "x,y,z,world"
                "is_for_sender INTEGER NOT NULL DEFAULT 1, " + // Boolean as INTEGER in SQLite (1 = true)
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        // Create triggers for all tables to handle updated_at
        String[] tables = { "players", "nation_vaults", "trades", "trade_vaults",
                "vault_npcs", "trade_vault_npcs" };
        for (String table : tables) {
            context.execute("CREATE TRIGGER IF NOT EXISTS update_" + table + "_timestamp " +
                    "AFTER UPDATE ON " + table + " " +
                    "BEGIN " +
                    "   UPDATE " + table + " SET updated_at = datetime('now') WHERE id = NEW.id; " +
                    "END");
        }
    }
}

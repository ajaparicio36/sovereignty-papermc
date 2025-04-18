package com.tatayless.sovereignty.database;

import com.tatayless.sovereignty.Sovereignty;
import org.bukkit.scheduler.BukkitRunnable;
import org.jooq.DSLContext;

import java.sql.Connection;
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
                // Don't use the passed context which might have a closed connection
                try (Connection connection = plugin.getDatabaseManager().getConnection()) {
                    DSLContext newContext = org.jooq.impl.DSL.using(
                            connection,
                            plugin.getDatabaseManager().getSqlDialect());

                    if (isMySQL) {
                        createMySQLTables(newContext);
                    } else {
                        createSQLiteTables(newContext);
                    }
                    plugin.getLogger().info("Database tables have been initialized");
                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
                    e.printStackTrace(); // Add stack trace for better debugging
                    future.completeExceptionally(e);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    private void createMySQLTables(DSLContext context) {
        context.execute("CREATE TABLE IF NOT EXISTS nations (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(64) NOT NULL UNIQUE, " +
                "power DOUBLE NOT NULL DEFAULT 1.0, " +
                "power_level INT NOT NULL DEFAULT 1, " +
                "claimed_chunks JSON, " +
                "annexed_chunks JSON, " + // Chunks annexed from other nations
                "alliances JSON, " +
                "wars JSON, " + // Active wars
                "president_id VARCHAR(36), " + // Store president reference directly
                "senators JSON, " + // Store senators as JSON array of player IDs
                "soldiers JSON, " + // Added soldiers as JSON array of player IDs
                "citizens JSON, " + // Added citizens as JSON array of player IDs
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS players (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(36) NOT NULL UNIQUE, " +
                "nation_id VARCHAR(36), " +
                "role VARCHAR(20), " + // Add role field (e.g., 'president', 'senator', 'soldier', 'citizen')
                "soldier_lives INT DEFAULT 0, " + // Track soldier lives for war
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE SET NULL" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS nation_vaults (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "nation_id VARCHAR(36) NOT NULL UNIQUE, " +
                "items JSON, " +
                "overflow_items JSON, " + // Items that exceed vault limit
                "overflow_expiry TIMESTAMP, " + // When overflow items expire
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS trades (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "sending_nation_id VARCHAR(36) NOT NULL, " +
                "receiving_nation_id VARCHAR(36) NOT NULL, " +
                "sending_items JSON, " +
                "receiving_items JSON, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'pending', " + // pending, completed, failed, cancelled
                "consecutive_trades INT NOT NULL DEFAULT 0, " + // Track consecutive trades for power scaling
                "last_execution TIMESTAMP, " + // Last time items were exchanged
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (sending_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (receiving_nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS trade_vaults (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "trade_id VARCHAR(36) NOT NULL UNIQUE, " +
                "sending_items_vault JSON, " +
                "receiving_items_vault JSON, " +
                "execution_interval INT NOT NULL DEFAULT 3, " + // Interval in minecraft days
                "next_execution TIMESTAMP, " + // Next scheduled execution time
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS vault_npcs (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "nation_id VARCHAR(36) NOT NULL UNIQUE, " +
                "nation_vault_id VARCHAR(36) NOT NULL UNIQUE, " +
                "coordinates VARCHAR(255) NOT NULL, " + // Store as "x,y,z,world"
                "entity_id INT, " + // Entity ID of the Villager NPC
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_vault_id) REFERENCES nation_vaults(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS trade_vault_npcs (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "trade_id VARCHAR(36) NOT NULL, " +
                "nation_id VARCHAR(36) NOT NULL, " +
                "coordinates VARCHAR(255) NOT NULL, " + // Store as "x,y,z,world"
                "entity_id INT, " + // Entity ID of the Villager NPC
                "is_for_sender BOOLEAN NOT NULL DEFAULT TRUE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS wars (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "attacker_nation_id VARCHAR(36) NOT NULL, " +
                "defender_nation_id VARCHAR(36) NOT NULL, " +
                "status VARCHAR(20) NOT NULL DEFAULT 'active', " + // active, ended, cancelled
                "attacker_kills INT NOT NULL DEFAULT 0, " +
                "defender_kills INT NOT NULL DEFAULT 0, " +
                "required_kills INT NOT NULL, " + // Kills required to win (based on opponent power level)
                "winner_id VARCHAR(36), " + // ID of winning nation
                "started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "ended_at TIMESTAMP, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (attacker_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (defender_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (winner_id) REFERENCES nations(id) ON DELETE SET NULL" +
                ")");
    }

    private void createSQLiteTables(DSLContext context) {
        context.execute("CREATE TABLE IF NOT EXISTS nations (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL UNIQUE, " +
                "power REAL NOT NULL DEFAULT 1.0, " +
                "power_level INTEGER NOT NULL DEFAULT 1, " +
                "claimed_chunks TEXT, " + // JSON array stored as TEXT in SQLite
                "annexed_chunks TEXT, " + // Chunks annexed from other nations
                "alliances TEXT, " + // JSON array stored as TEXT in SQLite
                "wars TEXT, " + // Active wars
                "president_id TEXT, " + // Store president reference directly
                "senators TEXT, " + // Store senators as JSON array of player IDs
                "soldiers TEXT, " + // Added soldiers as JSON array of player IDs
                "citizens TEXT, " + // Added citizens as JSON array of player IDs
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now'))" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS players (" +
                "id TEXT PRIMARY KEY, " +
                "name TEXT NOT NULL UNIQUE, " +
                "nation_id TEXT, " +
                "role TEXT, " + // Add role field (e.g., 'president', 'senator', 'soldier', 'citizen')
                "soldier_lives INTEGER DEFAULT 0, " + // Track soldier lives for war
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE SET NULL" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS nation_vaults (" +
                "id TEXT PRIMARY KEY, " +
                "nation_id TEXT NOT NULL UNIQUE, " +
                "items TEXT, " + // JSON as TEXT in SQLite
                "overflow_items TEXT, " + // Items that exceed vault limit
                "overflow_expiry TEXT, " + // When overflow items expire
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS trades (" +
                "id TEXT PRIMARY KEY, " +
                "sending_nation_id TEXT NOT NULL, " +
                "receiving_nation_id TEXT NOT NULL, " +
                "sending_items TEXT, " + // JSON as TEXT in SQLite
                "receiving_items TEXT, " + // JSON as TEXT in SQLite
                "status TEXT NOT NULL DEFAULT 'pending', " + // pending, completed, failed, cancelled
                "consecutive_trades INTEGER NOT NULL DEFAULT 0, " + // Track consecutive trades for power scaling
                "last_execution TEXT, " + // Last time items were exchanged
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (sending_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (receiving_nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS trade_vaults (" +
                "id TEXT PRIMARY KEY, " +
                "trade_id TEXT NOT NULL UNIQUE, " +
                "sending_items_vault TEXT, " + // JSON as TEXT in SQLite
                "receiving_items_vault TEXT, " + // JSON as TEXT in SQLite
                "execution_interval INTEGER NOT NULL DEFAULT 3, " + // Interval in minecraft days
                "next_execution TEXT, " + // Next scheduled execution time
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS vault_npcs (" +
                "id TEXT PRIMARY KEY, " +
                "nation_id TEXT NOT NULL UNIQUE, " +
                "nation_vault_id TEXT NOT NULL UNIQUE, " +
                "coordinates TEXT NOT NULL, " + // Store as "x,y,z,world"
                "entity_id INTEGER, " + // Entity ID of the Villager NPC
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_vault_id) REFERENCES nation_vaults(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS trade_vault_npcs (" +
                "id TEXT PRIMARY KEY, " +
                "trade_id TEXT NOT NULL, " +
                "nation_id TEXT NOT NULL, " +
                "coordinates TEXT NOT NULL, " + // Store as "x,y,z,world"
                "entity_id INTEGER, " + // Entity ID of the Villager NPC
                "is_for_sender INTEGER NOT NULL DEFAULT 1, " + // Boolean as INTEGER in SQLite (1 = true)
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (trade_id) REFERENCES trades(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (nation_id) REFERENCES nations(id) ON DELETE CASCADE" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS wars (" +
                "id TEXT PRIMARY KEY, " +
                "attacker_nation_id TEXT NOT NULL, " +
                "defender_nation_id TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'active', " + // active, ended, cancelled
                "attacker_kills INTEGER NOT NULL DEFAULT 0, " +
                "defender_kills INTEGER NOT NULL DEFAULT 0, " +
                "required_kills INTEGER NOT NULL, " + // Kills required to win (based on opponent power level)
                "winner_id TEXT, " + // ID of winning nation
                "started_at TEXT DEFAULT (datetime('now')), " +
                "ended_at TEXT, " +
                "created_at TEXT DEFAULT (datetime('now')), " +
                "updated_at TEXT DEFAULT (datetime('now')), " +
                "FOREIGN KEY (attacker_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (defender_nation_id) REFERENCES nations(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (winner_id) REFERENCES nations(id) ON DELETE SET NULL" +
                ")");

        String[] tables = { "nations", "players", "nation_vaults", "trades", "trade_vaults",
                "vault_npcs", "trade_vault_npcs", "wars" };
        for (String table : tables) {
            context.execute("CREATE TRIGGER IF NOT EXISTS update_" + table + "_timestamp " +
                    "AFTER UPDATE ON " + table + " " +
                    "BEGIN " +
                    "   UPDATE " + table + " SET updated_at = datetime('now') WHERE id = NEW.id; " +
                    "END");
        }
    }
}

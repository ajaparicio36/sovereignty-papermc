package com.tatayless.sovereignty.database.migration;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

public class InitialSchemaMigration implements Migration {

    private final boolean isMySQL;

    public InitialSchemaMigration(boolean isMySQL) {
        this.isMySQL = isMySQL;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Initial schema migration";
    }

    @Override
    public boolean apply(Connection connection, DSLContext context) throws SQLException {
        try {
            // First check if tables exist but haven't been migrated
            if (tableExists(connection, "nations")) {
                // Check if admin_set_power exists
                if (!columnExists(connection, "nations", "admin_set_power")) {
                    // Add the admin_set_power column to the nations table
                    if (isMySQL) {
                        context.execute(
                                "ALTER TABLE nations ADD COLUMN admin_set_power BOOLEAN NOT NULL DEFAULT FALSE");
                    } else {
                        context.execute("ALTER TABLE nations ADD COLUMN admin_set_power INTEGER NOT NULL DEFAULT 0");
                    }
                }
            } else {
                // Create all tables from scratch - no migration needed
                if (isMySQL) {
                    createMySQLTables(context);
                } else {
                    createSQLiteTables(context);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[] { "TABLE" })) {
            if (isMySQL) {
                return rs.next();
            } else {
                // SQLite table names are case-insensitive
                while (rs.next()) {
                    if (rs.getString("TABLE_NAME").equalsIgnoreCase(tableName)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            if (isMySQL) {
                return rs.next();
            } else {
                // SQLite column names are case-insensitive
                while (rs.next()) {
                    if (rs.getString("COLUMN_NAME").equalsIgnoreCase(columnName)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    private void createMySQLTables(DSLContext context) {
        context.execute("CREATE TABLE IF NOT EXISTS nations (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(64) NOT NULL UNIQUE, " +
                "power DOUBLE NOT NULL DEFAULT 1.0, " +
                "admin_set_power BOOLEAN NOT NULL DEFAULT FALSE, " + // Track if power was set by admin
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
                "items JSON, " + // Now stores a map of page indexes to item arrays
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
                "world TEXT, " + // Added for direct location storage
                "x DOUBLE, " + // Added for direct x coordinate
                "y DOUBLE, " + // Added for direct y coordinate
                "z DOUBLE, " + // Added for direct z coordinate
                "created_by VARCHAR(36), " + // Added creator's player ID
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
                "admin_set_power INTEGER NOT NULL DEFAULT 0, " + // SQLite uses INTEGER for boolean
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
                "items TEXT, " + // JSON as TEXT in SQLite - now stores a map of page indexes to item arrays
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
                "world TEXT, " + // Added for direct location storage
                "x DOUBLE, " + // Added for direct x coordinate
                "y DOUBLE, " + // Added for direct y coordinate
                "z DOUBLE, " + // Added for direct z coordinate
                "created_by TEXT, " + // Added creator's player ID
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

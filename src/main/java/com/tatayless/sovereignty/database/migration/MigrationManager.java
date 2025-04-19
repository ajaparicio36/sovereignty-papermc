package com.tatayless.sovereignty.database.migration;

import com.tatayless.sovereignty.Sovereignty;
import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MigrationManager {

    private final Sovereignty plugin;
    private final boolean isMySQL;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationManager(Sovereignty plugin, boolean isMySQL) {
        this.plugin = plugin;
        this.isMySQL = isMySQL;
    }

    /**
     * Register a migration
     * 
     * @param migration The migration to register
     */
    public void registerMigration(Migration migration) {
        migrations.add(migration);
        // Sort migrations by version number to ensure proper order
        migrations.sort(Comparator.comparingInt(Migration::getVersion));
    }

    /**
     * Create migration table if it doesn't exist
     * 
     * @param connection The database connection
     * @throws SQLException If there was an error creating the table
     */
    private void createMigrationTableIfNotExists(Connection connection) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                isMySQL
                        ? "CREATE TABLE IF NOT EXISTS migrations (" +
                                "version INT PRIMARY KEY, " +
                                "description VARCHAR(255) NOT NULL, " +
                                "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                                ")"
                        : "CREATE TABLE IF NOT EXISTS migrations (" +
                                "version INTEGER PRIMARY KEY, " +
                                "description TEXT NOT NULL, " +
                                "applied_at TEXT DEFAULT (datetime('now'))" +
                                ")")) {
            stmt.executeUpdate();
        }
    }

    /**
     * Get the current database schema version
     * 
     * @param connection The database connection
     * @return The current version or 0 if no migrations have been applied
     * @throws SQLException If there was an error querying the database
     */
    private int getCurrentVersion(Connection connection) throws SQLException {
        try {
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT MAX(version) FROM migrations")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            // Table might not exist yet
            return 0;
        }
        return 0;
    }

    /**
     * Record that a migration was applied
     * 
     * @param connection The database connection
     * @param migration  The migration that was applied
     * @throws SQLException If there was an error recording the migration
     */
    private void recordMigration(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO migrations (version, description) VALUES (?, ?)")) {
            stmt.setInt(1, migration.getVersion());
            stmt.setString(2, migration.getDescription());
            stmt.executeUpdate();
        }
    }

    /**
     * Apply all pending migrations
     * 
     * @param connection The database connection
     * @param context    The DSL context
     * @return True if all migrations were applied successfully
     */
    public boolean applyMigrations(Connection connection, DSLContext context) {
        try {
            // Create the migrations table if it doesn't exist
            createMigrationTableIfNotExists(connection);

            // Get the current version
            int currentVersion = getCurrentVersion(connection);
            plugin.getLogger().info("Current database schema version: " + currentVersion);

            // Apply all migrations that are newer than the current version
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                for (Migration migration : migrations) {
                    if (migration.getVersion() > currentVersion) {
                        plugin.getLogger().info(
                                "Applying migration " + migration.getVersion() + ": " + migration.getDescription());
                        if (migration.apply(connection, context)) {
                            recordMigration(connection, migration);
                            plugin.getLogger().info("Migration " + migration.getVersion() + " applied successfully");
                        } else {
                            plugin.getLogger().severe("Failed to apply migration " + migration.getVersion());
                            connection.rollback();
                            connection.setAutoCommit(autoCommit);
                            return false;
                        }
                    }
                }

                connection.commit();
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error applying migrations: " + e.getMessage());
                e.printStackTrace();
                connection.rollback();
                return false;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Database error during migrations: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}

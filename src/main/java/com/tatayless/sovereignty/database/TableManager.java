package com.tatayless.sovereignty.database;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.database.migration.InitialSchemaMigration;
import com.tatayless.sovereignty.database.migration.MigrationManager;
import com.tatayless.sovereignty.database.migration.VaultNpcsMigration;
import org.jooq.DSLContext;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;

public class TableManager {
        private final Sovereignty plugin;
        private final boolean isMySQL;
        private final MigrationManager migrationManager;

        public TableManager(Sovereignty plugin, boolean isMySQL) {
                this.plugin = plugin;
                this.isMySQL = isMySQL;
                this.migrationManager = new MigrationManager(plugin, isMySQL);

                // Register all migrations
                registerMigrations();
        }

        /**
         * Register migrations in order from oldest to newest
         */
        private void registerMigrations() {
                // Register the initial schema migration
                migrationManager.registerMigration(new InitialSchemaMigration(isMySQL));

                // Register the vault npcs migration
                migrationManager.registerMigration(new VaultNpcsMigration(isMySQL));

                // Add future migrations here in order of version number
                // Example: migrationManager.registerMigration(new SomeFutureMigration());
        }

        /**
         * Get the migration manager
         * 
         * @return The migration manager
         */
        public MigrationManager getMigrationManager() {
                return migrationManager;
        }

        public CompletableFuture<Void> createTables(DSLContext context) {
                CompletableFuture<Void> future = new CompletableFuture<>();

                // Execute on a separate thread to avoid blocking the main server thread
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getLogger().info("Creating tables asynchronously...");
                        // Don't use the passed context which might have a closed connection
                        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
                                plugin.getLogger().info("Got a fresh connection for table creation");
                                DSLContext newContext = org.jooq.impl.DSL.using(
                                                connection,
                                                plugin.getDatabaseManager().getSqlDialect());

                                // Run migrations first
                                plugin.getLogger().info("Checking for database migrations...");
                                boolean migrationsOk = migrationManager.applyMigrations(connection, newContext);
                                if (!migrationsOk) {
                                        plugin.getLogger().severe(
                                                        "Failed to apply some migrations - database may be in an inconsistent state");
                                }

                                plugin.getLogger().info("Database tables have been initialized successfully");
                                future.complete(null);
                        } catch (Exception e) {
                                plugin.getLogger().severe("Failed to create database tables: " + e.getMessage());
                                future.completeExceptionally(e);
                        }
                });

                return future;
        }
}

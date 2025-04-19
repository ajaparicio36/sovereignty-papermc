package com.tatayless.sovereignty.database;

import com.tatayless.sovereignty.Sovereignty;
import com.tatayless.sovereignty.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseManager {
    private final Sovereignty plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private SQLDialect sqlDialect;
    private TableManager tableManager;
    // Add a lock for SQLite connections
    private final ReentrantLock sqliteLock = new ReentrantLock();

    public DatabaseManager(Sovereignty plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void initialize() throws SQLException {
        setupDataSource();
        tableManager = new TableManager(plugin, configManager.isMySQL());
        createTablesIfNotExist();

        // Make sure migrations are applied during initialization
        try (Connection conn = getConnection()) {
            DSLContext context = createContextSafe(conn);
            boolean migrationsApplied = tableManager.getMigrationManager().applyMigrations(conn, context);
            if (!migrationsApplied) {
                throw new SQLException("Failed to apply database migrations");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to apply migrations: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Explicitly run database migrations.
     * This is useful when you want to force migrations to run
     * outside of the normal startup sequence.
     */
    public CompletableFuture<Boolean> runMigrations() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = getConnection()) {
                DSLContext context = createContextSafe(conn);
                boolean success = tableManager.getMigrationManager().applyMigrations(conn, context);
                future.complete(success);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to run migrations: " + e.getMessage());
                e.printStackTrace();
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    private void setupDataSource() {
        HikariConfig hikariConfig = new HikariConfig();

        if (configManager.isMySQL()) {
            // Configure MySQL connection
            hikariConfig.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s",
                    configManager.getMySQLHost(),
                    configManager.getMySQLPort(),
                    configManager.getMySQLDatabase()));
            hikariConfig.setUsername(configManager.getMySQLUsername());
            hikariConfig.setPassword(configManager.getMySQLPassword());
            hikariConfig.addDataSourceProperty("useSSL", String.valueOf(configManager.getMySQLUseSSL()));
            hikariConfig.addDataSourceProperty("serverTimezone", "UTC");
            hikariConfig.addDataSourceProperty("characterEncoding", "utf8");
            hikariConfig.addDataSourceProperty("useUnicode", "true");

            sqlDialect = SQLDialect.MYSQL;

            // MySQL-specific HikariCP configuration
            hikariConfig.setPoolName("SovereigntyMySQLPool");
            hikariConfig.setMinimumIdle(3);
            hikariConfig.setMaximumPoolSize(10);
        } else {
            // Configure SQLite connection with better settings
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, configManager.getSQLiteFilename());
            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            hikariConfig.setJdbcUrl(jdbcUrl);

            // SQLite-specific configurations - critical changes here
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setPoolName("SovereigntySQLitePool");

            // SQLite should only use 1 connection - this is critical
            hikariConfig.setMinimumIdle(1);
            hikariConfig.setMaximumPoolSize(1);

            // Make sure connections are properly reused
            hikariConfig.setConnectionTimeout(5000); // 5 seconds timeout
            hikariConfig.setIdleTimeout(60000); // 1 minute idle timeout
            hikariConfig.setMaxLifetime(60000 * 30); // 30 minutes max lifetime

            // Add these important SQLite-specific properties
            hikariConfig.addDataSourceProperty("journal_mode", "WAL"); // Write-Ahead Logging mode
            hikariConfig.addDataSourceProperty("synchronous", "NORMAL");
            hikariConfig.addDataSourceProperty("foreign_keys", "true");
            hikariConfig.addDataSourceProperty("cache_size", "1000");

            sqlDialect = SQLDialect.SQLITE;
        }

        // Common HikariCP configuration
        hikariConfig.setAutoCommit(true);
        hikariConfig.setLeakDetectionThreshold(60000); // 1 minute

        dataSource = new HikariDataSource(hikariConfig);

        plugin.getLogger().info("Database connection pool initialized for " +
                (configManager.isMySQL() ? "MySQL" : "SQLite"));
    }

    private void createTablesIfNotExist() throws SQLException {
        plugin.getLogger().info("Starting to create tables if they don't exist...");
        try (Connection conn = getConnection()) {
            plugin.getLogger().info("Got connection, creating context...");
            DSLContext context = DSL.using(conn, sqlDialect);
            plugin.getLogger().info("Calling tableManager.createTables...");

            // Change to synchronous table creation to ensure tables exist before continuing
            try {
                tableManager.createTablesSync(conn, context);
                plugin.getLogger().info("Tables created successfully");
            } catch (Exception ex) {
                plugin.getLogger().severe("Error in table creation: " + ex.getMessage());
                ex.printStackTrace();
                throw ex;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error setting up tables: " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Failed to create database tables", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (configManager.isSQLite()) {
            // For SQLite, implement a small wait if we can't get connection immediately
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5 seconds max wait time

            while (System.currentTimeMillis() - startTime < timeout) {
                try {
                    return dataSource.getConnection();
                } catch (SQLException e) {
                    if (!e.getMessage().contains("Connection is not available")) {
                        throw e; // If it's not a connection availability issue, rethrow
                    }

                    try {
                        Thread.sleep(100); // Wait a short time before trying again
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting for connection", ie);
                    }
                }
            }

            // If we get here, we couldn't get a connection within the timeout
            throw new SQLException("Could not obtain a connection within " + timeout + "ms");
        }

        // For MySQL, just get a connection normally
        return dataSource.getConnection();
    }

    /**
     * Creates a DSLContext with a connection that must be manually closed by the
     * caller.
     * Use this with try-with-resources to ensure the connection is closed.
     */
    public DSLContext createContext() throws SQLException {
        Connection conn = getConnection();
        return DSL.using(conn, sqlDialect);
    }

    /**
     * Creates a DSLContext with a provided connection.
     * The connection will NOT be closed by this method.
     */
    public DSLContext createContextSafe(Connection conn) {
        return DSL.using(conn, sqlDialect);
    }

    /**
     * Execute database operations safely for SQLite
     * This method handles the locking for SQLite operations
     */
    public <T> T executeWithLock(DatabaseOperation<T> operation) {
        if (configManager.isSQLite()) {
            sqliteLock.lock();
            try (Connection conn = getConnection()) {
                DSLContext context = createContextSafe(conn);
                return operation.execute(conn, context);
            } catch (SQLException e) {
                plugin.getLogger().severe("Database operation failed: " + e.getMessage());
                e.printStackTrace();
                return null;
            } finally {
                sqliteLock.unlock();
            }
        } else {
            try (Connection conn = getConnection()) {
                DSLContext context = createContextSafe(conn);
                return operation.execute(conn, context);
            } catch (SQLException e) {
                plugin.getLogger().severe("Database operation failed: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool has been shut down");
        }
    }

    public SQLDialect getSqlDialect() {
        return sqlDialect;
    }
}

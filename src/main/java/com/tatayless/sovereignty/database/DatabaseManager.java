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

public class DatabaseManager {
    private final Sovereignty plugin;
    private final ConfigManager configManager;
    private HikariDataSource dataSource;
    private SQLDialect sqlDialect;

    public DatabaseManager(Sovereignty plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void initialize() throws SQLException {
        setupDataSource();
        createTablesIfNotExist();
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
            // Configure SQLite connection
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, configManager.getSQLiteFilename());
            String jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            hikariConfig.setJdbcUrl(jdbcUrl);

            // SQLite-specific configurations
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setPoolName("SovereigntySQLitePool");
            hikariConfig.setMinimumIdle(1); // SQLite should only use 1 connection
            hikariConfig.setMaximumPoolSize(1); // SQLite only supports one connection

            sqlDialect = SQLDialect.SQLITE;
        }

        // Common HikariCP configuration for both database types
        hikariConfig.setAutoCommit(true);
        hikariConfig.setMaxLifetime(1800000); // 30 minutes
        hikariConfig.setConnectionTimeout(30000); // 30 seconds
        hikariConfig.setLeakDetectionThreshold(60000); // 1 minute

        dataSource = new HikariDataSource(hikariConfig);

        plugin.getLogger().info("Database connection pool initialized for " +
                (configManager.isMySQL() ? "MySQL" : "SQLite"));
    }

    private void createTablesIfNotExist() throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            DSLContext context = DSL.using(conn, sqlDialect);

            if (configManager.isMySQL()) {
                createMySQLTables(context);
            } else {
                createSQLiteTables(context);
            }
        } finally {
            // Manually close the connection
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }

    private void createMySQLTables(DSLContext context) {
        // MySQL version of tables with proper timestamp syntax
        context.execute("CREATE TABLE IF NOT EXISTS sovereignty_nations (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(64) NOT NULL UNIQUE, " +
                "description TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS sovereignty_players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "nation_id INTEGER, " +
                "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "last_login TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (nation_id) REFERENCES sovereignty_nations(id) ON DELETE SET NULL" +
                ")");
    }

    private void createSQLiteTables(DSLContext context) {
        // SQLite version of tables with proper timestamp handling
        context.execute("CREATE TABLE IF NOT EXISTS sovereignty_nations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name VARCHAR(64) NOT NULL UNIQUE, " +
                "description TEXT, " +
                "created_at TEXT DEFAULT (datetime('now', 'localtime'))" +
                ")");

        context.execute("CREATE TABLE IF NOT EXISTS sovereignty_players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "nation_id INTEGER, " +
                "joined_at TEXT DEFAULT (datetime('now', 'localtime')), " +
                "last_login TEXT DEFAULT (datetime('now', 'localtime')), " +
                "FOREIGN KEY (nation_id) REFERENCES sovereignty_nations(id) ON DELETE SET NULL" +
                ")");
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DSLContext createContext() throws SQLException {
        return DSL.using(getConnection(), sqlDialect);
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

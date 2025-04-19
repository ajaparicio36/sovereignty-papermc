package com.tatayless.sovereignty.database.migration;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class TradeVaultNpcsMigration implements Migration {

    private final boolean isMySQL;

    public TradeVaultNpcsMigration(boolean isMySQL) {
        this.isMySQL = isMySQL;
    }

    @Override
    public int getVersion() {
        return 4; // Next version after NationVaultsMigration (which is version 3)
    }

    @Override
    public String getDescription() {
        return "Add location columns to trade_vault_npcs table";
    }

    @Override
    public boolean apply(Connection connection, DSLContext context) throws SQLException {
        try {
            boolean success = true;

            // First check if trade_vault_npcs table exists
            if (tableExists(connection, "trade_vault_npcs")) {
                // Get existing column names to avoid adding duplicates
                Set<String> existingColumns = getExistingColumns(connection, "trade_vault_npcs");

                // Add each column if it doesn't already exist
                if (!existingColumns.contains("world")) {
                    try {
                        context.execute("ALTER TABLE trade_vault_npcs ADD COLUMN world TEXT");
                    } catch (Exception e) {
                        System.err.println("Error adding 'world' column: " + e.getMessage());
                        success = false;
                    }
                }

                if (!existingColumns.contains("x")) {
                    try {
                        context.execute("ALTER TABLE trade_vault_npcs ADD COLUMN x DOUBLE NOT NULL DEFAULT 0");
                    } catch (Exception e) {
                        System.err.println("Error adding 'x' column: " + e.getMessage());
                        success = false;
                    }
                }

                if (!existingColumns.contains("y")) {
                    try {
                        context.execute("ALTER TABLE trade_vault_npcs ADD COLUMN y DOUBLE NOT NULL DEFAULT 0");
                    } catch (Exception e) {
                        System.err.println("Error adding 'y' column: " + e.getMessage());
                        success = false;
                    }
                }

                if (!existingColumns.contains("z")) {
                    try {
                        context.execute("ALTER TABLE trade_vault_npcs ADD COLUMN z DOUBLE NOT NULL DEFAULT 0");
                    } catch (Exception e) {
                        System.err.println("Error adding 'z' column: " + e.getMessage());
                        success = false;
                    }
                }

                // Update existing coordinates from the coordinates column if possible
                try {
                    context.execute("UPDATE trade_vault_npcs SET " +
                            "world = SUBSTR(coordinates, INSTR(coordinates, ',', INSTR(coordinates, ',', INSTR(coordinates, ',')+1)+1)+1), "
                            +
                            "x = CAST(SUBSTR(coordinates, 1, INSTR(coordinates, ',')-1) AS DOUBLE), " +
                            "y = CAST(SUBSTR(coordinates, INSTR(coordinates, ',')+1, INSTR(coordinates, ',', INSTR(coordinates, ',')+1) - INSTR(coordinates, ',')-1) AS DOUBLE), "
                            +
                            "z = CAST(SUBSTR(coordinates, INSTR(coordinates, ',', INSTR(coordinates, ',')+1)+1, INSTR(coordinates, ',', INSTR(coordinates, ',', INSTR(coordinates, ',')+1)+1) - INSTR(coordinates, ',', INSTR(coordinates, ',')+1)-1) AS DOUBLE) "
                            +
                            "WHERE coordinates LIKE '%,%,%,%'");
                } catch (Exception e) {
                    System.err.println("Error updating coordinates: " + e.getMessage());
                    // Not critical, so don't fail the migration
                }
            }

            return success;
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

    private Set<String> getExistingColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();

        try (ResultSet rs = meta.getColumns(null, null, tableName, null)) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
        }

        return columns;
    }
}

package com.tatayless.sovereignty.database.migration;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class VaultNpcsMigration implements Migration {

    private final boolean isMySQL;

    public VaultNpcsMigration(boolean isMySQL) {
        this.isMySQL = isMySQL;
    }

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public String getDescription() {
        return "Add location columns to vault_npcs table";
    }

    @Override
    public boolean apply(Connection connection, DSLContext context) throws SQLException {
        try {
            // First check if table exists
            if (!tableExists(connection, "vault_npcs")) {
                // Table doesn't exist yet, it will be created with all columns in
                // InitialSchemaMigration
                return true;
            }

            // Get existing column names to avoid adding duplicates
            Set<String> existingColumns = getExistingColumns(connection, "vault_npcs");

            // Add each column if it doesn't already exist
            if (!existingColumns.contains("world")) {
                try {
                    context.execute("ALTER TABLE vault_npcs ADD COLUMN world TEXT");
                } catch (Exception e) {
                    // Column might already exist or other issue - log but continue
                    System.err.println("Error adding 'world' column: " + e.getMessage());
                }
            }

            if (!existingColumns.contains("x")) {
                try {
                    context.execute("ALTER TABLE vault_npcs ADD COLUMN x DOUBLE");
                } catch (Exception e) {
                    System.err.println("Error adding 'x' column: " + e.getMessage());
                }
            }

            if (!existingColumns.contains("y")) {
                try {
                    context.execute("ALTER TABLE vault_npcs ADD COLUMN y DOUBLE");
                } catch (Exception e) {
                    System.err.println("Error adding 'y' column: " + e.getMessage());
                }
            }

            if (!existingColumns.contains("z")) {
                try {
                    context.execute("ALTER TABLE vault_npcs ADD COLUMN z DOUBLE");
                } catch (Exception e) {
                    System.err.println("Error adding 'z' column: " + e.getMessage());
                }
            }

            if (!existingColumns.contains("created_by")) {
                try {
                    context.execute("ALTER TABLE vault_npcs ADD COLUMN created_by TEXT");
                } catch (Exception e) {
                    System.err.println("Error adding 'created_by' column: " + e.getMessage());
                }
            }

            if (!existingColumns.contains("nation_id")) {
                try {
                    context.execute("ALTER TABLE vault_npcs ADD COLUMN nation_id INTEGER NOT NULL");
                } catch (Exception e) {
                    System.err.println("Error adding 'nation_id' column: " + e.getMessage());
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

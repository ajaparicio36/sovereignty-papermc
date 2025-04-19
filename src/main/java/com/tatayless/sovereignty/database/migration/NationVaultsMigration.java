package com.tatayless.sovereignty.database.migration;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

public class NationVaultsMigration implements Migration {

    private final boolean isMySQL;

    public NationVaultsMigration(boolean isMySQL) {
        this.isMySQL = isMySQL;
    }

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public String getDescription() {
        return "Create or ensure nation_vaults table exists";
    }

    @Override
    public boolean apply(Connection connection, DSLContext context) throws SQLException {
        try {
            // Check if table exists
            if (!tableExists(connection, "nation_vaults")) {
                System.out.println("Creating nation_vaults table from dedicated migration");

                String createVaultsTable;
                if (isMySQL) {
                    createVaultsTable = "CREATE TABLE nation_vaults ("
                            + "id TEXT PRIMARY KEY, "
                            + "nation_id TEXT NOT NULL, "
                            + "items TEXT, "
                            + "overflow_items TEXT, "
                            + "overflow_expiry TIMESTAMP)";
                } else {
                    createVaultsTable = "CREATE TABLE nation_vaults ("
                            + "id TEXT PRIMARY KEY, "
                            + "nation_id TEXT NOT NULL, "
                            + "items TEXT, "
                            + "overflow_items TEXT, "
                            + "overflow_expiry TIMESTAMP)";
                }

                try {
                    context.execute(createVaultsTable);
                    return true;
                } catch (Exception e) {
                    System.err.println("Error creating nation_vaults table: " + e.getMessage());
                    e.printStackTrace();
                    return false;
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
}

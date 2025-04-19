package com.tatayless.sovereignty.database.migration;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Interface for database migrations
 */
public interface Migration {

    /**
     * Get the version number of the migration
     * 
     * @return The version number
     */
    int getVersion();

    /**
     * Get the description of the migration
     * 
     * @return The description
     */
    String getDescription();

    /**
     * Apply the migration to the database
     * 
     * @param connection The database connection
     * @param context    The DSL context
     * @return True if the migration was applied successfully
     * @throws SQLException If there was an error applying the migration
     */
    boolean apply(Connection connection, DSLContext context) throws SQLException;
}

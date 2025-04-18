package com.tatayless.sovereignty.database;

import org.jooq.DSLContext;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Functional interface for database operations
 * 
 * @param <T> The return type of the database operation
 */
public interface DatabaseOperation<T> {
    /**
     * Executes a database operation with the provided connection and DSL context
     * 
     * @param connection The database connection
     * @param context    The JOOQ DSL context
     * @return The result of the operation
     * @throws SQLException If a database error occurs
     */
    T execute(Connection connection, DSLContext context) throws SQLException;
}

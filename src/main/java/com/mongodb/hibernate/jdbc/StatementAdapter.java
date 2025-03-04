/*
 * Copyright 2024-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.hibernate.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import org.jspecify.annotations.Nullable;

interface StatementAdapter extends Statement {
    @Override
    default ResultSet executeQuery(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("executeQuery not implemented");
    }

    @Override
    default int executeUpdate(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate not implemented");
    }

    @Override
    default void close() throws SQLException {
        throw new SQLFeatureNotSupportedException("close not implemented");
    }

    @Override
    default int getMaxFieldSize() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxFieldSize not implemented");
    }

    @Override
    default void setMaxFieldSize(int max) throws SQLException {
        throw new SQLFeatureNotSupportedException("setMaxFieldSize not implemented");
    }

    @Override
    default int getMaxRows() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMaxRows not implemented");
    }

    @Override
    default void setMaxRows(int max) throws SQLException {
        throw new SQLFeatureNotSupportedException("setMaxRows not implemented");
    }

    @Override
    default void setEscapeProcessing(boolean enable) throws SQLException {
        throw new SQLFeatureNotSupportedException("setEscapeProcessing not implemented");
    }

    @Override
    default int getQueryTimeout() throws SQLException {
        throw new SQLFeatureNotSupportedException("getQueryTimeout not implemented");
    }

    @Override
    default void setQueryTimeout(int seconds) throws SQLException {
        throw new SQLFeatureNotSupportedException("setQueryTimeout not implemented");
    }

    @Override
    default void cancel() throws SQLException {
        throw new SQLFeatureNotSupportedException("cancel not implemented");
    }

    @Override
    default @Nullable SQLWarning getWarnings() throws SQLException {
        throw new SQLFeatureNotSupportedException("getWarnings not implemented");
    }

    @Override
    default void clearWarnings() throws SQLException {
        throw new SQLFeatureNotSupportedException("clearWarnings not implemented");
    }

    @Override
    default void setCursorName(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("setCursorName not implemented");
    }

    @Override
    default boolean execute(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("execute not implemented");
    }

    @Override
    default @Nullable ResultSet getResultSet() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSet not implemented");
    }

    @Override
    default int getUpdateCount() throws SQLException {
        throw new SQLFeatureNotSupportedException("getUpdateCount not implemented");
    }

    @Override
    default boolean getMoreResults() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMoreResults not implemented");
    }

    @Override
    default void setFetchDirection(int direction) throws SQLException {
        throw new SQLFeatureNotSupportedException("setFetchDirection not implemented");
    }

    @Override
    default int getFetchDirection() throws SQLException {
        throw new SQLFeatureNotSupportedException("getFetchDirection not implemented");
    }

    @Override
    default void setFetchSize(int rows) throws SQLException {
        throw new SQLFeatureNotSupportedException("setFetchSize not implemented");
    }

    @Override
    default int getFetchSize() throws SQLException {
        throw new SQLFeatureNotSupportedException("getFetchSize not implemented");
    }

    @Override
    default int getResultSetConcurrency() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetConcurrency not implemented");
    }

    @Override
    default int getResultSetType() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetType not implemented");
    }

    @Override
    default void addBatch(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("addBatch not implemented");
    }

    @Override
    default void clearBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("clearBatch not implemented");
    }

    @Override
    default int[] executeBatch() throws SQLException {
        throw new SQLFeatureNotSupportedException("executeBatch not implemented");
    }

    @Override
    default Connection getConnection() throws SQLException {
        throw new SQLFeatureNotSupportedException("getConnection not implemented");
    }

    @Override
    default boolean getMoreResults(int current) throws SQLException {
        throw new SQLFeatureNotSupportedException("getMoreResults not implemented");
    }

    @Override
    default ResultSet getGeneratedKeys() throws SQLException {
        throw new SQLFeatureNotSupportedException("getGeneratedKeys not implemented");
    }

    @Override
    default int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate not implemented");
    }

    @Override
    default int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate not implemented");
    }

    @Override
    default int executeUpdate(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate not implemented");
    }

    @Override
    default boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("execute not implemented");
    }

    @Override
    default boolean execute(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("execute not implemented");
    }

    @Override
    default boolean execute(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("execute not implemented");
    }

    @Override
    default int getResultSetHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetHoldability not implemented");
    }

    @Override
    default boolean isClosed() throws SQLException {
        throw new SQLFeatureNotSupportedException("isClosed not implemented");
    }

    @Override
    default void setPoolable(boolean poolable) throws SQLException {
        throw new SQLFeatureNotSupportedException("setPoolable not implemented");
    }

    @Override
    default boolean isPoolable() throws SQLException {
        throw new SQLFeatureNotSupportedException("isPoolable not implemented");
    }

    @Override
    default void closeOnCompletion() throws SQLException {
        throw new SQLFeatureNotSupportedException("closeOnCompletion not implemented");
    }

    @Override
    default boolean isCloseOnCompletion() throws SQLException {
        throw new SQLFeatureNotSupportedException("isCloseOnCompletion not implemented");
    }

    @Override
    default <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException("unwrap not implemented");
    }

    @Override
    default boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new SQLFeatureNotSupportedException("isWrapperFor not implemented");
    }
}

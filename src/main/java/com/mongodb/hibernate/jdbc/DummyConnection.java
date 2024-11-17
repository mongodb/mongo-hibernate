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

import java.sql.*;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * A dummy {@link java.sql.Connection} implementation class throwing exceptions for all its API methods.
 *
 * @see MongoConnection
 */
abstract class DummyConnection implements Connection {

    @Override
    public Statement createStatement() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void commit() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void rollback() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void close() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public boolean isClosed() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public @Nullable String getCatalog() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public @Nullable SQLWarning getWarnings() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void clearWarnings() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public int getHoldability() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Clob createClob() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        throw new SQLClientInfoException("won't be used", Collections.emptyMap());
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        throw new SQLClientInfoException("won't be used", Collections.emptyMap());
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public String getSchema() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("won't be used");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw new SQLException("won't be used");
    }
}

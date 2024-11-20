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

import static com.mongodb.hibernate.internal.MongoAssertions.fail;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;

/**
 * A {@link java.sql.Connection} implementation class that fails for all its API methods.
 *
 * @see MongoConnection
 */
abstract class ConnectionAdapter implements Connection {

    @Override
    public Statement createStatement() throws SQLException {
        throw fail();
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        throw fail();
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        throw fail();
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        throw fail();
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        throw fail();
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        throw fail();
    }

    @Override
    public void commit() throws SQLException {
        throw fail();
    }

    @Override
    public void rollback() throws SQLException {
        throw fail();
    }

    @Override
    public void close() throws SQLException {
        throw fail();
    }

    @Override
    public boolean isClosed() throws SQLException {
        throw fail();
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        throw fail();
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        throw fail();
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        throw fail();
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        throw fail();
    }

    @Override
    public @Nullable String getCatalog() throws SQLException {
        throw fail();
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        throw fail();
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        throw fail();
    }

    @Override
    public @Nullable SQLWarning getWarnings() throws SQLException {
        throw fail();
    }

    @Override
    public void clearWarnings() throws SQLException {
        throw fail();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        throw fail();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        throw fail();
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw fail();
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw fail();
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        throw fail();
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        throw fail();
    }

    @Override
    public int getHoldability() throws SQLException {
        throw fail();
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw fail();
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw fail();
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw fail();
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw fail();
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        throw fail();
    }

    @Override
    public PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw fail();
    }

    @Override
    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw fail();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throw fail();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw fail();
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throw fail();
    }

    @Override
    public Clob createClob() throws SQLException {
        throw fail();
    }

    @Override
    public Blob createBlob() throws SQLException {
        throw fail();
    }

    @Override
    public NClob createNClob() throws SQLException {
        throw fail();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        throw fail();
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        throw fail();
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
        throw fail();
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        throw fail();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw fail();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw fail();
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        throw fail();
    }

    @Override
    public @Nullable String getSchema() throws SQLException {
        throw fail();
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        throw fail();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        throw fail();
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        throw fail();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw fail();
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        throw fail();
    }
}

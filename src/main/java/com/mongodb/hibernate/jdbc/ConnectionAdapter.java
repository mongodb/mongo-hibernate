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
import java.sql.SQLFeatureNotSupportedException;
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

interface ConnectionAdapter extends Connection {

    @Override
    default Statement createStatement() throws SQLException {
        throw new SQLFeatureNotSupportedException("createStatement not implemented");
    }

    @Override
    default PreparedStatement prepareStatement(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement not implemented");
    }

    @Override
    default CallableStatement prepareCall(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareCall not implemented");
    }

    @Override
    default String nativeSQL(String sql) throws SQLException {
        throw new SQLFeatureNotSupportedException("nativeSQL not implemented");
    }

    @Override
    default void setAutoCommit(boolean autoCommit) throws SQLException {
        throw new SQLFeatureNotSupportedException("setAutoCommit not implemented");
    }

    @Override
    default boolean getAutoCommit() throws SQLException {
        throw new SQLFeatureNotSupportedException("getAutoCommit not implemented");
    }

    @Override
    default void commit() throws SQLException {
        throw new SQLFeatureNotSupportedException("commit not implemented");
    }

    @Override
    default void rollback() throws SQLException {
        throw new SQLFeatureNotSupportedException("rollback not implemented");
    }

    @Override
    default void close() throws SQLException {
        throw new SQLFeatureNotSupportedException("close not implemented");
    }

    @Override
    default boolean isClosed() throws SQLException {
        throw new SQLFeatureNotSupportedException("isClosed not implemented");
    }

    @Override
    default DatabaseMetaData getMetaData() throws SQLException {
        throw new SQLFeatureNotSupportedException("getMetaData not implemented");
    }

    @Override
    default void setReadOnly(boolean readOnly) throws SQLException {
        throw new SQLFeatureNotSupportedException("setReadOnly not implemented");
    }

    @Override
    default boolean isReadOnly() throws SQLException {
        throw new SQLFeatureNotSupportedException("isReadOnly not implemented");
    }

    @Override
    default void setCatalog(String catalog) throws SQLException {
        throw new SQLFeatureNotSupportedException("setCatalog not implemented");
    }

    @Override
    default @Nullable String getCatalog() throws SQLException {
        throw new SQLFeatureNotSupportedException("getCatalog not implemented");
    }

    @Override
    default void setTransactionIsolation(int level) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTransactionIsolation not implemented");
    }

    @Override
    default int getTransactionIsolation() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTransactionIsolation not implemented");
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
    default Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("createStatement not implemented");
    }

    @Override
    default PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement not implemented");
    }

    @Override
    default CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareCall not implemented");
    }

    @Override
    default Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLFeatureNotSupportedException("getTypeMap not implemented");
    }

    @Override
    default void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        throw new SQLFeatureNotSupportedException("setTypeMap not implemented");
    }

    @Override
    default void setHoldability(int holdability) throws SQLException {
        throw new SQLFeatureNotSupportedException("setHoldability not implemented");
    }

    @Override
    default int getHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("getHoldability not implemented");
    }

    @Override
    default Savepoint setSavepoint() throws SQLException {
        throw new SQLFeatureNotSupportedException("setSavepoint not implemented");
    }

    @Override
    default Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("setSavepoint not implemented");
    }

    @Override
    default void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("rollback not implemented");
    }

    @Override
    default void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLFeatureNotSupportedException("releaseSavepoint not implemented");
    }

    @Override
    default Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        throw new SQLFeatureNotSupportedException("createStatement not implemented");
    }

    @Override
    default PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement not implemented");
    }

    @Override
    default CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareCall not implemented");
    }

    @Override
    default PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement not implemented");
    }

    @Override
    default PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement not implemented");
    }

    @Override
    default PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement not implemented");
    }

    @Override
    default Clob createClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("createClob not implemented");
    }

    @Override
    default Blob createBlob() throws SQLException {
        throw new SQLFeatureNotSupportedException("createBlob not implemented");
    }

    @Override
    default NClob createNClob() throws SQLException {
        throw new SQLFeatureNotSupportedException("createNClob not implemented");
    }

    @Override
    default SQLXML createSQLXML() throws SQLException {
        throw new SQLFeatureNotSupportedException("createSQLXML not implemented");
    }

    @Override
    default boolean isValid(int timeout) throws SQLException {
        throw new SQLFeatureNotSupportedException("isValid not implemented");
    }

    @Override
    default void setClientInfo(String name, String value) throws SQLClientInfoException {
        throw new SQLClientInfoException("setClientInfo not implemented", Collections.emptyMap());
    }

    @Override
    default void setClientInfo(Properties properties) throws SQLClientInfoException {
        throw new SQLClientInfoException("setClientInfo not implemented", Collections.emptyMap());
    }

    @Override
    default String getClientInfo(String name) throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfo not implemented");
    }

    @Override
    default Properties getClientInfo() throws SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfo not implemented");
    }

    @Override
    default Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        throw new SQLFeatureNotSupportedException("createArrayOf not implemented");
    }

    @Override
    default Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        throw new SQLFeatureNotSupportedException("createStruct not implemented");
    }

    @Override
    default void setSchema(String schema) throws SQLException {
        throw new SQLFeatureNotSupportedException("setSchema not implemented");
    }

    @Override
    default @Nullable String getSchema() throws SQLException {
        throw new SQLFeatureNotSupportedException("getSchema not implemented");
    }

    @Override
    default void abort(Executor executor) throws SQLException {
        throw new SQLFeatureNotSupportedException("abort not implemented");
    }

    @Override
    default void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        throw new SQLFeatureNotSupportedException("setNetworkTimeout not implemented");
    }

    @Override
    default int getNetworkTimeout() throws SQLException {
        throw new SQLFeatureNotSupportedException("getNetworkTimeout not implemented");
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

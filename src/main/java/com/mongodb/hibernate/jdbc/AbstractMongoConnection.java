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

/** Abstract {@link java.sql.Connection} dummy implementation. */
abstract class AbstractMongoConnection implements Connection {

    @Override
    public String nativeSQL(String sql) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public int getHoldability() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        throw new SQLClientInfoException("Hibernate won't use this method", Collections.emptyMap());
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        throw new SQLClientInfoException("Hibernate won't use this method", Collections.emptyMap());
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public String getSchema() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        throw new SQLException("Hibernate won't use this method");
    }
}

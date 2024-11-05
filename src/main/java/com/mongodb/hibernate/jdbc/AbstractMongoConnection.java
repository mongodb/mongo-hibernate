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

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/** Abstract {@link java.sql.Connection} dummy implementation. */
abstract class AbstractMongoConnection implements Connection {

    @Override
    public String nativeSQL(String sql) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public boolean isReadOnly() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setCatalog(String catalog) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public String getCatalog() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setHoldability(int holdability) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public int getHoldability() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public Savepoint setSavepoint() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public Savepoint setSavepoint(String name) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void rollback(Savepoint savepoint) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public PreparedStatement prepareStatement(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public CallableStatement prepareCall(
            String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public boolean isValid(int timeout) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setClientInfo(String name, String value) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setClientInfo(Properties properties) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public String getClientInfo(String name) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public Properties getClientInfo() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setSchema(String schema) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public String getSchema() {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void abort(Executor executor) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) {
        throw new IllegalStateException("Hibernate won't use this method");
    }

    @Override
    public int getNetworkTimeout() {
        throw new IllegalStateException("Hibernate won't use this method");
    }
}

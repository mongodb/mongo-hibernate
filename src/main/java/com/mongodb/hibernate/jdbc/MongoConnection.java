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

import com.mongodb.client.ClientSession;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Struct;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.jspecify.annotations.Nullable;

/** MongoDB Dialect's JDBC {@linkplain java.sql.Connection connection} implementation class. */
final class MongoConnection extends AbstractMongoConnection {

    private final ClientSession clientSession;

    private boolean closed;

    MongoConnection(ClientSession clientSession) {
        this.clientSession = clientSession;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // transaction

    @Override
    public void setAutoCommit(boolean autoCommit) {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-30");
    }

    @Override
    public boolean getAutoCommit() {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-30");
    }

    @Override
    public void commit() {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-30");
    }

    @Override
    public void rollback() {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-30");
    }

    @Override
    public void setTransactionIsolation(int level) {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-30");
    }

    @Override
    public int getTransactionIsolation() {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-30");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // close() and isClosed()

    @Override
    public void close() throws SQLException {
        if (!this.closed) { // no-op if closed
            try {
                this.clientSession.close();
                this.closed = true;
            } catch (RuntimeException e) {
                throw new SQLException("Error closing connection", e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return this.closed;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Statement and PreparedStatement

    @Override
    public Statement createStatement() {
        return createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    }

    @Override
    public PreparedStatement prepareStatement(String mql) {
        return prepareStatement(mql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-16");
    }

    @Override
    public PreparedStatement prepareStatement(String mql, int resultSetType, int resultSetConcurrency) {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-13");
    }

    @Override
    public PreparedStatement prepareStatement(String mql, int autoGeneratedKeys) {
        throw new UnsupportedOperationException("Auto-generated key from MongoDB server side unsupported");
    }

    @Override
    public PreparedStatement prepareStatement(String mql, String[] columnNames) {
        throw new UnsupportedOperationException("Auto-generated key from MongoDB server side unsupported");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // SQL99 data types

    @Override
    public Clob createClob() {
        throw new NotYetImplementedException();
    }

    @Override
    public Blob createBlob() {
        throw new NotYetImplementedException();
    }

    @Override
    public NClob createNClob() {
        throw new NotYetImplementedException();
    }

    @Override
    public SQLXML createSQLXML() {
        throw new NotYetImplementedException();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) {
        throw new NotYetImplementedException();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) {
        throw new NotYetImplementedException();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Database meta data

    @Override
    public DatabaseMetaData getMetaData() {
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-37");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // unsupported operations

    @Override
    public CallableStatement prepareCall(String mql) {
        throw new UnsupportedOperationException(
                "MongoDB's JSON server function has been deprecated in favour of 'aggregate' pipeline");
    }

    @Override
    public CallableStatement prepareCall(String mql, int resultSetType, int resultSetConcurrency) {
        throw new UnsupportedOperationException(
                "MongoDB's JSON server function has been deprecated in favour of 'aggregate' pipeline");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // dummy implementations

    @Override
    public @Nullable SQLWarning getWarnings() {
        return null;
    }

    @Override
    public void clearWarnings() {
        // no-op
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnknownUnwrapTypeException(unwrapType);
    }

    @Override
    public boolean isWrapperFor(Class<?> unwrapType) {
        return false;
    }
}

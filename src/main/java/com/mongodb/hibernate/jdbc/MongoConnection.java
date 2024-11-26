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
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Struct;
import org.hibernate.type.descriptor.java.JavaType;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@linkplain java.sql.Connection connection} implementation class.
 *
 * <p>It only focuses on API methods Hibernate ever used. All the unused methods are implemented by throwing exceptions
 * in its parent class.
 */
final class MongoConnection extends ConnectionAdapter {

    private final MongoClient mongoClient;
    private final ClientSession clientSession;

    private boolean closed;

    private boolean autoCommit;

    MongoConnection(MongoClient mongoClient, ClientSession clientSession) {
        this.mongoClient = mongoClient;
        this.clientSession = clientSession;
        autoCommit = true;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // transaction

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkClosed();
        if (autoCommit == this.autoCommit) {
            return;
        }
        doCommitIfNeeded();
        this.autoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        return autoCommit;
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("AutoCommit state should be false when committing transaction");
        }
        doCommitIfNeeded();
    }

    private void doCommitIfNeeded() throws SQLException {
        if (!clientSession.hasActiveTransaction()) {
            return;
        }
        try {
            clientSession.commitTransaction();
        } catch (RuntimeException e) {
            throw new SQLException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        if (autoCommit) {
            throw new SQLException("AutoCommit state should be false when committing transaction");
        }
        if (!clientSession.hasActiveTransaction()) {
            return;
        }
        try {
            clientSession.abortTransaction();
        } catch (RuntimeException e) {
            throw new SQLException("Failed to rollback transaction", e);
        }
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("MongoDB doesn't support JDBC transaction isolation level");
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("MongoDB doesn't support JDBC transaction isolation level");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // close() and isClosed()

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            try {
                clientSession.close();
            } catch (RuntimeException e) {
                throw new SQLException("Error closing connection", e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Statement and PreparedStatement

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new MongoStatement(mongoClient, clientSession, this);
    }

    @Override
    public PreparedStatement prepareStatement(String mql) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-13");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        return new MongoStatement(mongoClient, clientSession, this);
    }

    @Override
    public PreparedStatement prepareStatement(String mql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-13");
    }

    @Override
    public PreparedStatement prepareStatement(String mql, int autoGeneratedKeys) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Auto-generated key from MongoDB server side unsupported");
    }

    @Override
    public PreparedStatement prepareStatement(String mql, String[] columnNames) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Auto-generated key from MongoDB server side unsupported");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // SQL99 data types

    @Override
    public Clob createClob() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public Blob createBlob() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public NClob createNClob() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Database meta data

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-37");
    }

    /**
     * Used during Hibernate's DDL step for Information Extraction purposes.
     *
     * @see org.hibernate.tool.schema.extract.internal.AbstractInformationExtractorImpl
     */
    @Override
    public @Nullable String getCatalog() throws SQLException {
        checkClosed();
        return null;
    }

    /**
     * Used during Hibernate's DDL step for Information Extraction purposes.
     *
     * @see org.hibernate.tool.schema.extract.internal.AbstractInformationExtractorImpl
     */
    @Override
    public @Nullable String getSchema() throws SQLException {
        checkClosed();
        return null;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // unsupported operations

    // stored procedure equivalence in MongoDB won't be supported
    // see https://www.mongodb.com/resources/products/capabilities/stored-procedures

    @Override
    public CallableStatement prepareCall(String mql) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Stored procedure equivalence in MongoDB unsupported");
    }

    @Override
    public CallableStatement prepareCall(String mql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Stored procedure equivalence in MongoDB unsupported");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // dummy implementations

    /**
     * Only used in {@link org.hibernate.engine.jdbc.spi.SqlExceptionHelper}.
     *
     * <p>Currently no need arises to record warning in this connection class.
     */
    @Override
    public @Nullable SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    /** Only used in {@link org.hibernate.engine.jdbc.spi.SqlExceptionHelper}. */
    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    /** Only used in {@link org.hibernate.dialect.OracleArrayJdbcType#getBinder(JavaType)} */
    @Override
    public <T> T unwrap(Class<T> unwrapType) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("unwrap unsupported");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        checkClosed();
        return false;
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection has been closed");
        }
    }

    /**
     * Starts transaction for the first {@link java.sql.Statement} executing when {@code autoCommit} field value is
     * {@code false}.
     *
     * @see MongoStatement#executeQuery(String)
     * @see MongoStatement#executeUpdate(String)
     * @see MongoStatement#execute(String)
     * @see MongoStatement#executeBatch()
     */
    void startTransactionIfNeeded() throws SQLException {
        if (!autoCommit && !clientSession.hasActiveTransaction()) {
            try {
                clientSession.startTransaction();
            } catch (RuntimeException e) {
                throw new SQLException("Failed to start transaction", e);
            }
        }
    }
}

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

import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLWarning;
import java.util.Set;
import org.bson.BsonDocument;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@link java.sql.Statement} implementation class.
 *
 * <p>It only focuses on API methods Hibernate ever used. All the unused methods are implemented by throwing exceptions
 * in its parent class.
 */
final class MongoStatement extends StatementAdapter {

    // temporary hard-coded database prior to the db config tech design finalizing
    private static final String DATABASE = "mongo-hibernate-test";

    private static final Set<String> UPDATE_COMMAND_TYPES_SUPPORTED = Set.of("insert", "update", "delete");
    private final MongoClient mongoClient;
    private final MongoConnection mongoConnection;
    private final ClientSession clientSession;

    private boolean closed;

    public MongoStatement(MongoClient mongoClient, ClientSession clientSession, MongoConnection mongoConnection) {
        this.mongoClient = mongoClient;
        this.mongoConnection = mongoConnection;
        this.clientSession = clientSession;
    }

    @Override
    public ResultSet executeQuery(String mql) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int executeUpdate(String mql) throws SQLException {
        checkClosed();
        var command = parse(mql);
        var commandType = command.getFirstKey();
        if (!UPDATE_COMMAND_TYPES_SUPPORTED.contains(commandType)) {
            throw new SQLFeatureNotSupportedException(format(
                    "Unsupported update command type: %s (supported: %s)",
                    commandType, UPDATE_COMMAND_TYPES_SUPPORTED));
        }
        mongoConnection.startTransactionIfNeeded();
        try {
            var result = mongoClient.getDatabase(DATABASE).runCommand(clientSession, command);
            return result.getInteger("n");
        } catch (RuntimeException e) {
            throw new SQLException(format("Failed to execute update MQL [%s]", mql), e);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
        }
    }

    @Override
    public int getMaxRows() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void cancel() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

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

    // ----------------------- Multiple Results --------------------------

    @Override
    public boolean execute(String mql) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public @Nullable ResultSet getResultSet() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void addBatch(String mql) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkClosed();
        return mongoConnection;
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("Auto-generated key from MongoDB server side unsupported");
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

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
            throw new ClosedSQLException(this.getClass());
        }
    }

    private static BsonDocument parse(String mql) throws SQLException {
        try {
            return BsonDocument.parse(mql);
        } catch (RuntimeException e) {
            throw new SQLSyntaxErrorException("Invalid MQL: " + mql, e);
        }
    }
}
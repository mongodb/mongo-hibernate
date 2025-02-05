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

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.BuildConfig;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.sql.Array;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.sql.Struct;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@linkplain java.sql.Connection connection} implementation class.
 *
 * <p>It only focuses on API methods Mongo Dialect will support. All the other methods are implemented by throwing
 * exceptions in its parent {@linkplain ConnectionAdapter adapter interface}.
 */
final class MongoConnection implements ConnectionAdapter {

    // TODO-HIBERNATE-38 temporary hard-coded database prior to the db config tech design finalizing
    public static final String DATABASE = "mongo-hibernate-test";

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
        return new MongoPreparedStatement(mongoClient, clientSession, this, mql);
    }

    @Override
    public PreparedStatement prepareStatement(String mql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // SQL99 data types

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
        try {
            var commandResult = mongoClient
                    .getDatabase("admin")
                    .runCommand(clientSession, new BsonDocument("buildinfo", new BsonInt32(1)));
            var versionText = commandResult.getString("version");
            var versionArray = commandResult.getList("versionArray", Integer.class);
            if (versionArray.size() < 2) {
                throw new SQLException(
                        format("Unexpected versionArray [%s] field length (should be 2 or more)", versionArray));
            }
            return new MongoDatabaseMetaData(
                    this, versionText, versionArray.get(0), versionArray.get(1), assertNotNull(BuildConfig.VERSION));
        } catch (RuntimeException e) {
            // TODO-HIBERNATE-43 Let's do `LOGGER.error(<message>, e)`.
            // Hibernate ORM neither propagates, nor logs `e` (the cause of the `SQLException` we throw),
            // so if we fail to get `DatabaseMetaData` due to being unable to connect to a MongoDB deployment,
            // there is no easy way to know that.
            throw new SQLException("Failed to get metadata", e);
        }
    }

    @Override
    public @Nullable String getCatalog() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public @Nullable String getSchema() throws SQLException {
        checkClosed();
        return null;
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
}

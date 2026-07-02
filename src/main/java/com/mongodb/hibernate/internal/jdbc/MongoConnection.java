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

package com.mongodb.hibernate.internal.jdbc;

import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.cfg.MongoConfiguration;
import java.sql.Array;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.jspecify.annotations.Nullable;

final class MongoConnection implements ConnectionAdapter {

    /**
     * The BSON {@code undefined} value in ({@linkplain org.bson.json.JsonMode#EXTENDED extended}) JSON, used to model a
     * query parameter marker (see {@code AstParameterMarker}). A parameter renders as the JDBC standard {@code ?} in
     * MQL (see {@code MongoConstants#EXTENDED_JSON_WRITER_SETTINGS}), which {@link #translateParameterMarkers} rewrites
     * to this before parsing so that {@code MongoPreparedStatement} can bind it.
     */
    private static final String PARAMETER_MARKER = "{\"$undefined\": true}";

    private final MongoClient mongoClient;
    private final ClientSession clientSession;
    private final MongoDatabase mongoDatabase;
    private boolean closed;

    private boolean autoCommit;

    MongoConnection(MongoConfiguration config, MongoClient mongoClient, ClientSession clientSession) {
        this.mongoClient = mongoClient;
        this.clientSession = clientSession;
        mongoDatabase = mongoClient.getDatabase(config.databaseName());
        autoCommit = true;
    }

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

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new MongoStatement(mongoDatabase, clientSession, this);
    }

    @Override
    public PreparedStatement prepareStatement(String mql) throws SQLException {
        checkClosed();
        return prepareStatement(mql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    }

    /**
     * Rewrites the JDBC parameter markers that Hibernate hard-codes into native queries into MQL.
     *
     * <p>Hibernate always renders parameters using {@code ?} marker, and wraps a multi-valued (collection) parameter in
     * a SQL-style parenthesized list, e.g. {@code $in: [(?,?,?)]}. Neither {@code ?} nor the surrounding parentheses
     * are valid MQL, so before parsing we replace each {@code ?} with the BSON {@code undefined} marker (which
     * {@link MongoPreparedStatement} binds) and drop the list-wrapping parentheses, turning {@code $in: [(?,?,?)]} into
     * {@code $in: [{"$undefined": true},{"$undefined": true},{"$undefined": true}]}.
     *
     * <p>A {@code ?}, {@code (} or {@code )} is rewritten only when it is structural MQL syntax, i.e. outside a string
     * literal; the same characters inside a double-quoted string, e.g. {@code "a?b"}, are copied verbatim (respecting
     * {@code \} escapes).
     *
     * <p>Native queries must be written using MongoDB <a
     * href="https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/">Extended JSON</a>.
     */
    static String translateParameterMarkers(String mql) {
        StringBuilder result = new StringBuilder(mql.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < mql.length(); i++) {
            var c = mql.charAt(i);
            if (inString) {
                result.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
                result.append(c);
            } else if (c == '?') {
                result.append(PARAMETER_MARKER);
            } else if (c != '(' && c != ')') {
                result.append(c);
            }
        }
        return result.toString();
    }

    @Override
    public PreparedStatement prepareStatement(String mql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        checkClosed();
        if (resultSetType != ResultSet.TYPE_FORWARD_ONLY) {
            throw new SQLFeatureNotSupportedException(
                    "Unsupported result set type (only TYPE_FORWARD_ONLY is supported): " + resultSetType);
        }
        if (resultSetConcurrency != ResultSet.CONCUR_READ_ONLY) {
            throw new SQLFeatureNotSupportedException(
                    "Unsupported result set concurrency (only CONCUR_READ_ONLY is supported): " + resultSetConcurrency);
        }
        return new MongoPreparedStatement(mongoDatabase, clientSession, this, translateParameterMarkers(mql));
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkClosed();
        return new MongoArray(elements);
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        try {
            var commandResult = mongoClient
                    .getDatabase("admin")
                    .runCommand(clientSession, new BsonDocument("buildInfo", new BsonInt32(1)));
            var versionText = commandResult.getString("version");
            var versionArray = commandResult.getList("versionArray", Integer.class);
            if (versionArray.size() < 2) {
                throw new SQLException(
                        format("Unexpected versionArray [%s] field length (should be 2 or more)", versionArray));
            }
            return new MongoDatabaseMetaData(this, versionText, versionArray.get(0), versionArray.get(1));
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

    @Override
    public @Nullable SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    // Hibernate 7's `ExtractedDatabaseMetaDataImpl` queries this when building boot-time JDBC metadata.
    // MongoDB has no JDBC-style transaction isolation level, so report TRANSACTION_NONE.
    @Override
    public int getTransactionIsolation() throws SQLException {
        checkClosed();
        return TRANSACTION_NONE;
    }

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
            throw new SQLException(format("%s has been closed", getClass().getSimpleName()));
        }
    }
}

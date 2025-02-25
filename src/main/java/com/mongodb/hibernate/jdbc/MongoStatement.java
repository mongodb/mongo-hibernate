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

import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@link java.sql.Statement} implementation class.
 *
 * <p>It only focuses on API methods Mongo Dialect will support. All the other methods are implemented by throwing
 * exceptions in its parent {@link StatementAdapter adapter interface}.
 */
class MongoStatement implements StatementAdapter {

    private final MongoDatabase mongoDatabase;
    private final MongoConnection mongoConnection;
    private final ClientSession clientSession;

    private @Nullable ResultSet resultSet;
    private boolean closed;

    MongoStatement(MongoDatabase mongoDatabase, ClientSession clientSession, MongoConnection mongoConnection) {
        this.mongoDatabase = mongoDatabase;
        this.mongoConnection = mongoConnection;
        this.clientSession = clientSession;
    }

    @Override
    public ResultSet executeQuery(String mql) throws SQLException {
        checkClosed();
        var command = parse(mql);
        return executeQueryCommand(command);
    }

    ResultSet executeQueryCommand(BsonDocument command) throws SQLException {
        startTransactionIfNeeded();
        try {
            var collectionName = command.getString("aggregate").getValue();
            var collection = mongoClient.getDatabase(DATABASE).getCollection(collectionName, BsonDocument.class);
            var pipelineArray = command.getArray("pipeline");

            var pipeline = new ArrayList<BsonDocument>(pipelineArray.size());
            pipelineArray.forEach(bsonValue -> pipeline.add(bsonValue.asDocument()));

            var cursor = collection.aggregate(clientSession, pipeline).cursor();
            var fieldNames = getFieldNamesFromProjectDocument(
                    pipeline.get(pipeline.size() - 1).getDocument("$project"));
            resultSet = new MongoResultSet(cursor, fieldNames);
            return resultSet;
        } catch (RuntimeException e) {
            throw new SQLException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Gets included field names from {@code $project} document
     *
     * @param projectDocument the {@code $project} document
     * @return ordered field names included in {@code aggregate} command response
     */
    private List<String> getFieldNamesFromProjectDocument(BsonDocument projectDocument) {
        var fieldNames = new ArrayList<String>(projectDocument.size());
        projectDocument.forEach((key, value) -> {
            boolean skip = (value.isNumber() && value.asNumber().intValue() == 0)
                    || (value.isBoolean() && value.asBoolean().equals(BsonBoolean.FALSE));
            if (!skip) {
                fieldNames.add(key);
            }
        });
        return fieldNames;
    }

    @Override
    public int executeUpdate(String mql) throws SQLException {
        checkClosed();
        var command = parse(mql);
        return executeUpdateCommand(command);
    }

    int executeUpdateCommand(BsonDocument command) throws SQLException {
        startTransactionIfNeeded();
        try {
            return mongoDatabase.runCommand(clientSession, command).getInteger("n");
        } catch (Exception e) {
            throw new SQLException("Failed to execute update command: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    @Override
    public int getMaxRows() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void cancel() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException();
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
        throw new FeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public @Nullable ResultSet getResultSet() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void addBatch(String mql) throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-35 https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-35 https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-35 https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkClosed();
        return mongoConnection;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        checkClosed();
        return false;
    }

    void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException(format("%s has been closed", getClass().getSimpleName()));
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    MongoDatabase getMongoDatabase() {
        return mongoDatabase;
    }

    static BsonDocument parse(String mql) throws SQLSyntaxErrorException {
        try {
            return BsonDocument.parse(mql);
        } catch (RuntimeException e) {
            throw new SQLSyntaxErrorException("Invalid MQL: " + mql, e);
        }
    }

    /**
     * Starts transaction for the first {@link java.sql.Statement} executing if
     * {@linkplain MongoConnection#getAutoCommit() auto-commit} is disabled.
     */
    private void startTransactionIfNeeded() throws SQLException {
        if (!mongoConnection.getAutoCommit() && !clientSession.hasActiveTransaction()) {
            clientSession.startTransaction();
        }
    }
}

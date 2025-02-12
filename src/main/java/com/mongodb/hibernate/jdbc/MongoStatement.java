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
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.MongoSocketWriteTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.jspecify.annotations.Nullable;

class MongoStatement implements StatementAdapter {

    private final MongoDatabase mongoDatabase;
    private final MongoConnection mongoConnection;
    private final ClientSession clientSession;

    private boolean closed;

    MongoStatement(MongoDatabase mongoDatabase, ClientSession clientSession, MongoConnection mongoConnection) {
        this.mongoDatabase = mongoDatabase;
        this.mongoConnection = mongoConnection;
        this.clientSession = clientSession;
    }

    @Override
    public ResultSet executeQuery(String mql) throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int executeUpdate(String mql) throws SQLException {
        checkClosed();
        var command = parse(mql);
        return executeUpdateCommand(command);
    }

    int executeUpdateCommand(BsonDocument command) throws SQLException {
        var bulkWriteResult = executeBulkWrite(singletonList(command));
        return switch (command.getFirstKey()) {
            case "insert" -> bulkWriteResult.getInsertedCount();
            case "update" -> bulkWriteResult.getModifiedCount();
            case "delete" -> bulkWriteResult.getDeletedCount();
            default -> throw new FeatureNotSupportedException("Unsupported command: " + command.getFirstKey());
        };
    }

    BulkWriteResult executeBulkWrite(List<? extends BsonDocument> commandBatch) throws SQLException {
        startTransactionIfNeeded();

        try {
            var writeModels = new ArrayList<WriteModel<BsonDocument>>(commandBatch.size());

            var commandName = assertNotNull(commandBatch.get(0).getFirstKey());
            var collectionName =
                    assertNotNull(commandBatch.get(0).getString(commandName).getValue());

            for (var command : commandBatch) {

                assertTrue(commandName.equals(command.getFirstKey()));
                assertTrue(collectionName.equals(command.getString(commandName).getValue()));

                switch (commandName) {
                    case "insert":
                        var documents = command.getArray("documents");
                        for (var document : documents) {
                            writeModels.add(new InsertOneModel<>((BsonDocument) document));
                        }
                        break;
                    case "update":
                        var updates = command.getArray("updates").getValues();
                        for (var update : updates) {
                            var updateDocument = (BsonDocument) update;
                            WriteModel<BsonDocument> updateModel =
                                    !updateDocument.getBoolean("multi").getValue()
                                            ? new UpdateOneModel<>(
                                                    updateDocument.getDocument("q"), updateDocument.getDocument("u"))
                                            : new UpdateManyModel<>(
                                                    updateDocument.getDocument("q"), updateDocument.getDocument("u"));
                            writeModels.add(updateModel);
                        }
                        break;
                    case "delete":
                        var deletes = command.getArray("deletes");
                        for (var delete : deletes) {
                            var deleteDocument = (BsonDocument) delete;
                            writeModels.add(
                                    deleteDocument.getNumber("limit").intValue() == 1
                                            ? new DeleteOneModel<>(deleteDocument.getDocument("q"))
                                            : new DeleteManyModel<>(deleteDocument.getDocument("q")));
                        }
                        break;
                    default:
                        throw new FeatureNotSupportedException();
                }
            }
            return mongoDatabase
                    .getCollection(collectionName, BsonDocument.class)
                    .bulkWrite(clientSession, writeModels);
        } catch (MongoSocketReadTimeoutException
                | MongoSocketWriteTimeoutException
                | MongoTimeoutException
                | MongoExecutionTimeoutException e) {
            throw new SQLTimeoutException(e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to execute update", e);
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

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
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static com.mongodb.hibernate.jdbc.MongoConnection.DATABASE;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.List;
import org.bson.BsonDocument;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@link java.sql.Statement} implementation class.
 *
 * <p>It only focuses on API methods Mongo Dialect will support. All the other methods are implemented by throwing
 * exceptions in its parent {@link StatementAdapter adapter interface}.
 */
class MongoStatement implements StatementAdapter {

    private final MongoClient mongoClient;
    private final MongoConnection mongoConnection;
    private final ClientSession clientSession;

    private boolean closed;

    MongoStatement(MongoClient mongoClient, ClientSession clientSession, MongoConnection mongoConnection) {
        this.mongoClient = mongoClient;
        this.mongoConnection = mongoConnection;
        this.clientSession = clientSession;
    }

    ClientSession getClientSession() {
        return clientSession;
    }

    @Override
    public ResultSet executeQuery(String mql) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
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
            default -> throw new NotYetImplementedException();
        };
    }

    BulkWriteResult executeBulkWrite(List<? extends BsonDocument> commandBatch) throws SQLException {
        startTransactionIfNeeded();

        try {
            var writeModels = new ArrayList<WriteModel<BsonDocument>>(commandBatch.size());

            // Hibernate will group PreparedStatement by both table and mutation type
            var commandName = assertNotNull(commandBatch.get(0).getFirstKey());
            var collectionName =
                    assertNotNull(commandBatch.get(0).getString(commandName).getValue());

            for (var command : commandBatch) {

                assertTrue(commandName.equals(command.getFirstKey()));
                assertTrue(collectionName.equals(command.getString(commandName).getValue()));

                List<WriteModel<BsonDocument>> subWriteModels;

                switch (commandName) {
                    case "insert":
                        var documents = command.getArray("documents");
                        subWriteModels = new ArrayList<>(documents.size());
                        for (var document : documents) {
                            subWriteModels.add(new InsertOneModel<>((BsonDocument) document));
                        }
                        break;
                    case "update":
                        var updates = command.getArray("updates").getValues();
                        subWriteModels = new ArrayList<>(updates.size());
                        for (var update : updates) {
                            var updateDocument = (BsonDocument) update;
                            WriteModel<BsonDocument> updateModel =
                                    !updateDocument.getBoolean("multi").getValue()
                                            ? new UpdateOneModel<>(
                                                    updateDocument.getDocument("q"), updateDocument.getDocument("u"))
                                            : new UpdateManyModel<>(
                                                    updateDocument.getDocument("q"), updateDocument.getDocument("u"));
                            subWriteModels.add(updateModel);
                        }
                        break;
                    case "delete":
                        var deletes = command.getArray("deletes");
                        subWriteModels = new ArrayList<>(deletes.size());
                        for (var delete : deletes) {
                            var deleteDocument = (BsonDocument) delete;
                            subWriteModels.add(
                                    deleteDocument.getNumber("limit").intValue() == 1
                                            ? new DeleteOneModel<>(deleteDocument.getDocument("q"))
                                            : new DeleteManyModel<>(deleteDocument.getDocument("q")));
                        }
                        break;
                    default:
                        throw new NotYetImplementedException();
                }
                writeModels.addAll(subWriteModels);
            }
            return getMongoDatabase()
                    .getCollection(collectionName, BsonDocument.class)
                    .bulkWrite(getClientSession(), writeModels);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to run bulk write: " + e.getMessage(), e);
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
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
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
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("TODO-HIBERNATE-21 https://jira.mongodb.org/browse/HIBERNATE-21");
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
        return mongoClient.getDatabase(DATABASE);
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
    void startTransactionIfNeeded() throws SQLException {
        if (!mongoConnection.getAutoCommit() && !clientSession.hasActiveTransaction()) {
            clientSession.startTransaction();
        }
    }
}

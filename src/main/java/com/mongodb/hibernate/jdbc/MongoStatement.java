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
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.MongoSocketWriteTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.DeleteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLWarning;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

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
        closeLastOpenResultSet();
        var command = parse(mql);
        return executeQueryCommand(command);
    }

    void closeLastOpenResultSet() throws SQLException {
        if (resultSet != null && !resultSet.isClosed()) {
            resultSet.close();
        }
    }

    ResultSet executeQueryCommand(BsonDocument command) throws SQLException {
        try {
            startTransactionIfNeeded();

            var collectionName = command.getString("aggregate").getValue();
            var collection = mongoDatabase.getCollection(collectionName, BsonDocument.class);

            var pipeline = command.getArray("pipeline").stream()
                    .map(BsonValue::asDocument)
                    .toList();
            var fieldNames = getFieldNamesFromProjectStage(
                    pipeline.get(pipeline.size() - 1).getDocument("$project"));

            return resultSet = new MongoResultSet(
                    collection.aggregate(clientSession, pipeline).cursor(), fieldNames);
        } catch (RuntimeException e) {
            throw new SQLException("Failed to execute query", e);
        }
    }

    @VisibleForTesting(otherwise = PRIVATE)
    static List<String> getFieldNamesFromProjectStage(BsonDocument projectStage) {
        var fieldNames = projectStage.entrySet().stream()
                .filter(specification -> !isExcludeProjectSpecification(specification))
                .map(Map.Entry::getKey)
                .collect(toCollection(ArrayList::new));
        if (!projectStage.containsKey(ID_FIELD_NAME)) {
            // MongoDB includes this field unless it is explicitly excluded
            fieldNames.add(ID_FIELD_NAME);
        }
        return fieldNames;
    }

    private static boolean isExcludeProjectSpecification(Map.Entry<String, BsonValue> specification) {
        var key = specification.getKey();
        var value = specification.getValue();
        var exclude = (value.isBoolean() && !value.asBoolean().getValue())
                || (value.isNumber() && value.asNumber().intValue() == 0);
        if (exclude && !key.equals(ID_FIELD_NAME)) {
            throw new RuntimeException(format(
                    "Exclusions are not allowed in `$project` specifications, except for the [%s] field: [%s, %s]",
                    ID_FIELD_NAME, key, value));
        }
        if (!value.isBoolean() && !value.isNumber()) {
            throw new FeatureNotSupportedException(format(
                    "Expressions and literals are not supported in `$project` specifications: [%s: %s]", key, value));
        }
        return exclude;
    }

    @Override
    public int executeUpdate(String mql) throws SQLException {
        checkClosed();
        closeLastOpenResultSet();
        var command = parse(mql);
        return executeUpdateCommand(command);
    }

    int executeUpdateCommand(BsonDocument command) throws SQLException {
        try {
            var bulkWriteResult = executeBulkWrite(singletonList(command));
            return getUpdateCount(command.getFirstKey(), bulkWriteResult);
        } catch (MongoBulkWriteException mongoBulkWriteException) {
            throw new SQLException(mongoBulkWriteException.getMessage(), mongoBulkWriteException);
        }
    }

    BulkWriteResult executeBulkWrite(List<? extends BsonDocument> commandBatch) throws SQLException {
        startTransactionIfNeeded();
        var firstDocumentInBatch = commandBatch.get(0);
        var commandName = assertNotNull(firstDocumentInBatch.getFirstKey());
        var collectionName =
                assertNotNull(firstDocumentInBatch.getString(commandName).getValue());
        MongoCollection<BsonDocument> collection = mongoDatabase.getCollection(collectionName, BsonDocument.class);

        try {
            var writeModels = new ArrayList<WriteModel<BsonDocument>>(commandBatch.size());
            for (var command : commandBatch) {
                assertTrue(collectionName.equals(command.getString(commandName).getValue()));
                convertToWriteModels(command, commandName, writeModels);
            }
            return collection.bulkWrite(clientSession, writeModels);
        } catch (MongoSocketReadTimeoutException
                | MongoSocketWriteTimeoutException
                | MongoTimeoutException
                | MongoExecutionTimeoutException mongoTimeoutException) {
            throw new SQLTimeoutException(mongoTimeoutException.getMessage(), mongoTimeoutException);
        } catch (MongoBulkWriteException mongoBulkWriteException) {
            throw mongoBulkWriteException;
        } catch (RuntimeException runtimeException) {
            throw new SQLException("Failed to execute update", runtimeException);
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
    public void cancel() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public @Nullable SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public boolean execute(String mql) throws SQLException {
        checkClosed();
        closeLastOpenResultSet();
        throw new SQLFeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public @Nullable ResultSet getResultSet() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("To be implemented in scope of index and unique constraint creation");
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
    void startTransactionIfNeeded() throws SQLException {
        if (!mongoConnection.getAutoCommit() && !clientSession.hasActiveTransaction()) {
            clientSession.startTransaction();
        }
    }

    static int getUpdateCount(final String commandName, final BulkWriteResult bulkWriteResult) {
        return switch (commandName) {
            case "insert" -> bulkWriteResult.getInsertedCount();
            case "update" -> bulkWriteResult.getModifiedCount();
            case "delete" -> bulkWriteResult.getDeletedCount();
            default -> throw new FeatureNotSupportedException("Unsupported command: " + commandName);
        };
    }

    private static void convertToWriteModels(
            final BsonDocument command,
            final String commandName,
            final Collection<WriteModel<BsonDocument>> writeModels)
            throws SQLFeatureNotSupportedException {
        switch (commandName) {
            case "insert":
                var documents = command.getArray("documents");
                for (var insertDocument : documents) {
                    writeModels.add(createInsertModel(insertDocument.asDocument()));
                }
                break;
            case "update":
                var updates = command.getArray("updates").getValues();
                for (var updateDocument : updates) {
                    writeModels.add(createUpdateModel(updateDocument.asDocument()));
                }
                break;
            case "delete":
                var deletes = command.getArray("deletes");
                for (var deleteDocument : deletes) {
                    writeModels.add(createDeleteModel(deleteDocument.asDocument()));
                }
                break;
            default:
                throw new SQLFeatureNotSupportedException("Unsupported command: " + commandName);
        }
    }

    private static WriteModel<BsonDocument> createInsertModel(final BsonDocument document) {
        return new InsertOneModel<>(document);
    }

    private static WriteModel<BsonDocument> createDeleteModel(final BsonDocument deleteDocument) {
        var isSingleDelete = deleteDocument.getNumber("limit").intValue() == 1;
        var queryFilter = deleteDocument.getDocument("q");

        if (isSingleDelete) {
            new DeleteOptions();
            return new DeleteOneModel<>(queryFilter);
        }
        return new DeleteManyModel<>(queryFilter);
    }

    private static WriteModel<BsonDocument> createUpdateModel(final BsonDocument updateDocument) {
        var isMulti = updateDocument.getBoolean("multi").getValue();
        var queryFilter = updateDocument.getDocument("q");
        var updatePipeline = updateDocument.getDocument("u");

        if (isMulti) {
            return new UpdateManyModel<>(queryFilter, updatePipeline);
        }
        return new UpdateOneModel<>(queryFilter, updatePipeline);
    }
}

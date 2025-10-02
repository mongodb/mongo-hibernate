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

import static com.mongodb.assertions.Assertions.fail;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
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
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

class MongoStatement implements StatementAdapter {

    private static final List<String> SUPPORTED_UPDATE_COMMAND_ELEMENTS = List.of("q", "u", "multi");
    private static final List<String> SUPPORTED_DELETE_COMMAND_ELEMENTS = List.of("q", "limit");
    private static final String EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION = "Failed to execute operation";
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
        var commandType = getCommandType(command);
        checkSupportedQueryCommand(command);
        try {
            startTransactionIfNeeded();
            var collection = getCollection(commandType, command);
            var pipeline = command.getArray("pipeline").stream()
                    .map(BsonValue::asDocument)
                    .toList();
            var fieldNames = getFieldNamesFromProjectStage(
                    pipeline.get(pipeline.size() - 1).getDocument("$project"));

            return resultSet = new MongoResultSet(
                    collection.aggregate(clientSession, pipeline).cursor(), fieldNames);
        } catch (RuntimeException exception) {
            throw handleException(exception, commandType, ExecutionType.QUERY);
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
        checkSupportedUpdateCommand(command);
        return executeUpdateCommand(command);
    }

    int executeUpdateCommand(BsonDocument command) throws SQLException {
        return executeBulkWrite(singletonList(command), ExecutionType.UPDATE);
    }

    int executeBulkWrite(List<? extends BsonDocument> commandBatch, ExecutionType executionType) throws SQLException {
        var firstDocumentInBatch = commandBatch.get(0);
        var commandType = getCommandType(firstDocumentInBatch);
        var collection = getCollection(commandType, firstDocumentInBatch);
        try {
            startTransactionIfNeeded();
            var writeModels = new ArrayList<WriteModel<BsonDocument>>(commandBatch.size());
            for (var command : commandBatch) {
                convertToWriteModels(commandType, command, writeModels);
            }
            var bulkWriteResult = collection.bulkWrite(clientSession, writeModels);
            return getUpdateCount(commandType, bulkWriteResult);
        } catch (RuntimeException exception) {
            throw handleException(exception, commandType, executionType);
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

    private void checkSupportedQueryCommand(BsonDocument command) throws SQLFeatureNotSupportedException {
        var commandType = getCommandType(command);
        if (commandType != CommandType.AGGREGATE) {
            throw new SQLFeatureNotSupportedException(
                    format("Unsupported command for query operation: %s", commandType.getCommandName()));
        }
    }

    void checkSupportedUpdateCommand(BsonDocument command) throws SQLException {
        checkSupportedUpdateCommand(getCommandType(command));
    }

    private void checkSupportedUpdateCommand(CommandType commandType) throws SQLException {
        if (commandType != CommandType.INSERT
                && commandType != CommandType.UPDATE
                && commandType != CommandType.DELETE) {
            throw new SQLFeatureNotSupportedException(
                    format("Unsupported command for batch operation: %s", commandType.getCommandName()));
        }
    }

    void checkSupportedBatchCommand(BsonDocument command) throws SQLException {
        var commandType = getCommandType(command);
        if (commandType == CommandType.AGGREGATE) {
            // The method executeBatch throws a BatchUpdateException if any of the commands in the batch attempts to
            // return a result set.
            throw new BatchUpdateException(
                    format(
                            "Commands returning result set are not supported. Received command: %s",
                            commandType.getCommandName()),
                    null,
                    0,
                    null);
        }
        checkSupportedUpdateCommand(commandType);
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

    static CommandType getCommandType(BsonDocument command) throws SQLFeatureNotSupportedException {
        // The first key is always the command name, e.g. "insert", "update", "delete".
        return CommandType.fromString(assertNotNull(command.getFirstKey()));
    }

    private MongoCollection<BsonDocument> getCollection(CommandType commandType, BsonDocument command) {
        var collectionName =
                assertNotNull(command.getString(commandType.getCommandName()).getValue());
        return mongoDatabase.getCollection(collectionName, BsonDocument.class);
    }

    private static SQLException handleException(
            RuntimeException exception, CommandType commandType, ExecutionType executionType) {
        if (isTimeoutException(exception)) {
            return new SQLTimeoutException("Timeout while waiting for operation to complete", exception);
        }

        if (exception instanceof MongoBulkWriteException mongoBulkWriteException) {
            if (executionType == ExecutionType.BATCH) {
                return createBatchUpdateException(mongoBulkWriteException, commandType);
            } else {
                return new SQLException(
                        EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION,
                        null,
                        getErrorCode(mongoBulkWriteException),
                        mongoBulkWriteException);
            }
        }

        return new SQLException(EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION, exception);
    }

    private static boolean isTimeoutException(final RuntimeException exception) {
        return exception instanceof MongoSocketReadTimeoutException
                || exception instanceof MongoSocketWriteTimeoutException
                || exception instanceof MongoTimeoutException
                || exception instanceof MongoExecutionTimeoutException;
    }

    static BatchUpdateException createBatchUpdateException(
            MongoBulkWriteException mongoBulkWriteException, CommandType commandType) {
        var updateCount = getUpdateCount(commandType, mongoBulkWriteException.getWriteResult());
        var updateCounts = new int[updateCount];
        Arrays.fill(updateCounts, Statement.SUCCESS_NO_INFO);
        var code = getErrorCode(mongoBulkWriteException);
        return new BatchUpdateException(
                EXCEPTION_MESSAGE_FAILED_TO_EXECUTE_OPERATION, null, code, updateCounts, mongoBulkWriteException);
    }

    private static int getErrorCode(final MongoBulkWriteException mongoBulkWriteException) {
        var writeErrors = mongoBulkWriteException.getWriteErrors();
        // Since we are executing an ordered bulk write, there will be at most one BulkWriteError.
        return writeErrors.isEmpty() ? 0 : writeErrors.get(0).getCode();
    }

    private static void convertToWriteModels(
            CommandType commandType, BsonDocument command, Collection<WriteModel<BsonDocument>> writeModels)
            throws SQLFeatureNotSupportedException {
        switch (commandType) {
            case INSERT:
                var documents = command.getArray("documents");
                for (var insertDocument : documents) {
                    writeModels.add(createInsertModel(insertDocument.asDocument()));
                }
                break;
            case UPDATE:
                var updates = command.getArray("updates").getValues();
                for (var updateDocument : updates) {
                    writeModels.add(createUpdateModel(updateDocument.asDocument()));
                }
                break;
            case DELETE:
                var deletes = command.getArray("deletes");
                for (var deleteDocument : deletes) {
                    writeModels.add(createDeleteModel(deleteDocument.asDocument()));
                }
                break;
            default:
                throw fail();
        }
    }

    private static WriteModel<BsonDocument> createInsertModel(final BsonDocument insertDocument) {
        return new InsertOneModel<>(insertDocument);
    }

    private static WriteModel<BsonDocument> createDeleteModel(final BsonDocument deleteDocument)
            throws SQLFeatureNotSupportedException {
        checkDeleteElements(deleteDocument);
        var isSingleDelete = deleteDocument.getNumber("limit").intValue() == 1;
        var queryFilter = deleteDocument.getDocument("q");

        if (isSingleDelete) {
            return new DeleteOneModel<>(queryFilter);
        }
        return new DeleteManyModel<>(queryFilter);
    }

    private static WriteModel<BsonDocument> createUpdateModel(final BsonDocument updateDocument)
            throws SQLFeatureNotSupportedException {
        checkUpdateElements(updateDocument);
        var isMulti = updateDocument.getBoolean("multi").getValue();
        var queryFilter = updateDocument.getDocument("q");
        var updatePipeline = updateDocument.getDocument("u");

        if (isMulti) {
            return new UpdateManyModel<>(queryFilter, updatePipeline);
        }
        return new UpdateOneModel<>(queryFilter, updatePipeline);
    }

    private static void checkDeleteElements(final BsonDocument deleteDocument) throws SQLFeatureNotSupportedException {
        if (deleteDocument.size() > SUPPORTED_DELETE_COMMAND_ELEMENTS.size()) {
            var unSupportedElements = getUnsupportedElements(deleteDocument, SUPPORTED_DELETE_COMMAND_ELEMENTS);
            throw new SQLFeatureNotSupportedException(
                    format("Unsupported elements in delete command: %s", unSupportedElements));
        }
    }

    private static void checkUpdateElements(final BsonDocument updateDocument) throws SQLFeatureNotSupportedException {
        if (updateDocument.size() > SUPPORTED_UPDATE_COMMAND_ELEMENTS.size()) {
            var unSupportedElements = getUnsupportedElements(updateDocument, SUPPORTED_UPDATE_COMMAND_ELEMENTS);
            throw new SQLFeatureNotSupportedException(
                    format("Unsupported elements in update command: %s", unSupportedElements));
        }
    }

    private static List<String> getUnsupportedElements(
            final BsonDocument deleteDocument, final List<String> supportedElements) {
        return deleteDocument.keySet().stream()
                .filter((key) -> !supportedElements.contains(key))
                .toList();
    }

    static int getUpdateCount(CommandType commandType, BulkWriteResult bulkWriteResult) {
        return switch (commandType) {
            case INSERT -> bulkWriteResult.getInsertedCount();
            case UPDATE -> bulkWriteResult.getModifiedCount();
            case DELETE -> bulkWriteResult.getDeletedCount();
            default -> throw fail();
        };
    }

    enum CommandType {
        INSERT("insert"),
        UPDATE("update"),
        DELETE("delete"),
        AGGREGATE("aggregate");

        private final String commandName;

        CommandType(String commandName) {
            this.commandName = commandName;
        }

        String getCommandName() {
            return commandName;
        }

        static CommandType fromString(String commandName) throws SQLFeatureNotSupportedException {
            return switch (commandName) {
                case "insert" -> INSERT;
                case "update" -> UPDATE;
                case "delete" -> DELETE;
                case "aggregate" -> AGGREGATE;
                default -> throw new SQLFeatureNotSupportedException(format("Unsupported command: %s", commandName));
            };
        }
    }

    enum ExecutionType {
        UPDATE,
        BATCH,
        QUERY
    }
}

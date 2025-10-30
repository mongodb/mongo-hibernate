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

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.internal.MongoAssertions.assertTrue;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.MongoConstants.EXTENDED_JSON_WRITER_SETTINGS;
import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toCollection;
import static org.bson.BsonBoolean.FALSE;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
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
import com.mongodb.hibernate.internal.dialect.MongoAggregateSupport;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.bson.BSONException;
import org.bson.BsonDocument;
import org.bson.BsonInvalidOperationException;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

class MongoStatement implements StatementAdapter {
    private static final String EXCEPTION_MESSAGE_PREFIX_INVALID_MQL = "Invalid MQL";
    private static final String EXCEPTION_MESSAGE_OPERATION_FAILED = "Failed to execute operation";
    private static final String EXCEPTION_MESSAGE_OPERATION_TIMED_OUT =
            "Timeout while waiting for operation to complete";
    static final int NO_ERROR_CODE = 0;
    static final int[] EMPTY_UPDATE_COUNTS = new int[0];

    static final @Nullable String NULL_SQL_STATE = null;

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
        MongoAggregateSupport.checkSupported(mql);
        var command = parse(mql);
        checkSupportedQueryCommand(command);
        return executeQuery(command);
    }

    void closeLastOpenResultSet() throws SQLException {
        if (resultSet != null && !resultSet.isClosed()) {
            resultSet.close();
        }
    }

    ResultSet executeQuery(BsonDocument command) throws SQLException {
        try {
            var commandDescription = getCommandDescription(command);
            var collection = getCollection(commandDescription, command);
            var pipeline = command.getArray("pipeline").stream()
                    .map(BsonValue::asDocument)
                    .toList();
            var projectStageIndex = pipeline.size() - 1;
            if (pipeline.isEmpty()) {
                throw createSyntaxErrorException("%s. $project stage is missing [%s]", command, null);
            }
            var fieldNames = getFieldNamesFromProjectStage(
                    pipeline.get(projectStageIndex).getDocument("$project"));
            startTransactionIfNeeded();
            return resultSet = new MongoResultSet(
                    collection.aggregate(clientSession, pipeline).cursor(), fieldNames);
        } catch (BSONException bsonException) {
            throw createSyntaxErrorException("%s: [%s]", command, bsonException);
        } catch (RuntimeException exception) {
            throw handleExecuteQueryOrUpdateException(exception);
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
        MongoAggregateSupport.checkSupported(mql);
        var command = parse(mql);
        checkSupportedUpdateCommand(command);
        return executeUpdate(command);
    }

    int[] executeBatch(List<BsonDocument> commandBatch) throws SQLException {
        WriteModelsToCommandMapper writeModelsToCommandMapper = null;
        try {
            var firstCommandInBatch = commandBatch.get(0);
            var commandBatchSize = commandBatch.size();
            var commandDescription = getCommandDescription(firstCommandInBatch);
            var collection = getCollection(commandDescription, firstCommandInBatch);
            var writeModels = new ArrayList<WriteModel<BsonDocument>>(commandBatchSize);
            writeModelsToCommandMapper = new WriteModelsToCommandMapper(commandBatchSize);
            for (var command : commandBatch) {
                WriteModelConverter.convertToWriteModels(commandDescription, command, writeModels);
                writeModelsToCommandMapper.add(writeModels.size());
            }
            startTransactionIfNeeded();
            collection.bulkWrite(clientSession, writeModels);
            return createUpdateCounts(commandBatchSize);
        } catch (RuntimeException exception) {
            throw handleExecuteBatchException(exception, writeModelsToCommandMapper);
        }
    }

    private static int[] createUpdateCounts(int updateCountsSize) {
        // We cannot determine the actual number of rows affected for each command in the batch.
        var updateCounts = new int[updateCountsSize];
        Arrays.fill(updateCounts, Statement.SUCCESS_NO_INFO);
        return updateCounts;
    }

    int executeUpdate(BsonDocument command) throws SQLException {
        try {
            var commandDescription = getCommandDescription(command);
            var collection = getCollection(commandDescription, command);
            var writeModels = new ArrayList<WriteModel<BsonDocument>>();
            WriteModelConverter.convertToWriteModels(commandDescription, command, writeModels);
            startTransactionIfNeeded();
            var bulkWriteResult = collection.bulkWrite(clientSession, writeModels);
            return getUpdateCount(commandDescription, bulkWriteResult);
        } catch (RuntimeException exception) {
            throw handleExecuteQueryOrUpdateException(exception);
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
        MongoAggregateSupport.checkSupported(mql);
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-66 https://jira.mongodb.org/browse/HIBERNATE-66");
    }

    @Override
    public @Nullable ResultSet getResultSet() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-66 https://jira.mongodb.org/browse/HIBERNATE-66");
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-66 https://jira.mongodb.org/browse/HIBERNATE-66");
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-66 https://jira.mongodb.org/browse/HIBERNATE-66");
    }

    @Override
    public void addBatch(String mql) throws SQLException {
        checkClosed();
        MongoAggregateSupport.checkSupported(mql);
        throw new SQLFeatureNotSupportedException();
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

    static void checkSupportedQueryCommand(BsonDocument command)
            throws SQLFeatureNotSupportedException, SQLSyntaxErrorException {
        var commandDescription = getCommandDescription(command);
        if (commandDescription.isUpdate()) {
            throw new SQLFeatureNotSupportedException(
                    "Unsupported command for executeQuery: %s".formatted(commandDescription.getCommandName()));
        }
    }

    static void checkSupportedUpdateCommand(BsonDocument command)
            throws SQLFeatureNotSupportedException, SQLSyntaxErrorException {
        CommandDescription commandDescription = getCommandDescription(command);
        if (commandDescription.isQuery()) {
            throw new SQLFeatureNotSupportedException(
                    "Unsupported command for executeUpdate: %s".formatted(commandDescription.getCommandName()));
        }
    }

    static BsonDocument parse(String mql) throws SQLSyntaxErrorException {
        try {
            return BsonDocument.parse(mql);
        } catch (RuntimeException exception) {
            throw createSyntaxErrorException("%s: [%s]", mql, exception);
        }
    }

    /**
     * Starts transaction for the first {@link Statement} executing if {@linkplain MongoConnection#getAutoCommit()
     * auto-commit} is disabled.
     */
    private void startTransactionIfNeeded() throws SQLException {
        if (!mongoConnection.getAutoCommit() && !clientSession.hasActiveTransaction()) {
            clientSession.startTransaction();
        }
    }

    /** The first key is always the command name, e.g. "insert", "update", "delete". */
    static CommandDescription getCommandDescription(BsonDocument command)
            throws SQLFeatureNotSupportedException, SQLSyntaxErrorException {
        String commandName;
        try {
            commandName = command.getFirstKey();
        } catch (NoSuchElementException exception) {
            throw createSyntaxErrorException("%s. Command name is missing: [%s]", command, exception);
        }
        return CommandDescription.of(commandName);
    }

    private MongoCollection<BsonDocument> getCollection(CommandDescription commandDescription, BsonDocument command)
            throws SQLSyntaxErrorException {
        var commandName = commandDescription.getCommandName();
        BsonString collectionName;
        try {
            collectionName = command.getString(commandName);
        } catch (BsonInvalidOperationException exception) {
            throw createSyntaxErrorException("%s. Collection name is missing [%s]", command, exception);
        }
        return mongoDatabase.getCollection(collectionName.getValue(), BsonDocument.class);
    }

    private static int getUpdateCount(CommandDescription commandDescription, BulkWriteResult bulkWriteResult) {
        return switch (commandDescription) {
            case INSERT -> bulkWriteResult.getInsertedCount();
            case UPDATE -> bulkWriteResult.getModifiedCount();
            case DELETE -> bulkWriteResult.getDeletedCount();
            default -> throw fail();
        };
    }

    private static SQLException handleExecuteBatchException(
            RuntimeException exceptionToHandle, @Nullable WriteModelsToCommandMapper writeModelsToCommandMapper) {
        var errorCode = getErrorCode(exceptionToHandle);
        String exceptionMessage = getExceptionMessage(errorCode, exceptionToHandle);
        if (exceptionToHandle instanceof MongoBulkWriteException bulkWriteException) {
            return createBatchUpdateException(
                    exceptionMessage, errorCode, bulkWriteException, assertNotNull(writeModelsToCommandMapper));
        }

        // TODO-HIBERNATE-132 java.sql.BatchUpdateException is thrown when one of the
        // commands fails to execute properly. When exception is not of MongoBulkWriteException,
        // we are not sure if any command was executed successfully or failed.
        return new SQLException(exceptionMessage, NULL_SQL_STATE, errorCode, exceptionToHandle);
    }

    private static SQLException handleExecuteQueryOrUpdateException(RuntimeException exceptionToHandle) {
        var errorCode = getErrorCode(exceptionToHandle);
        String exceptionMessage = getExceptionMessage(errorCode, exceptionToHandle);
        return new SQLException(exceptionMessage, NULL_SQL_STATE, errorCode, exceptionToHandle);
    }

    private static String getExceptionMessage(int errorCode, RuntimeException exceptionToHandle) {
        if (exceptionToHandle instanceof MongoException mongoException && isTimeoutException(mongoException)) {
            return EXCEPTION_MESSAGE_OPERATION_TIMED_OUT;
        }
        var errorCategory = ErrorCategory.fromErrorCode(errorCode);
        return switch (errorCategory) {
            case DUPLICATE_KEY, UNCATEGORIZED -> EXCEPTION_MESSAGE_OPERATION_FAILED;
            // TODO-HIBERNATE-132 EXECUTION_TIMEOUT code is returned from the server. Do we know how many commands were
            // executed successfully so we can return it as BatchUpdateException?
            case EXECUTION_TIMEOUT -> EXCEPTION_MESSAGE_OPERATION_TIMED_OUT;
        };
    }

    private static int getErrorCode(RuntimeException runtimeException) {
        if (runtimeException instanceof MongoBulkWriteException mongoBulkWriteException) {
            var writeErrors = mongoBulkWriteException.getWriteErrors();
            if (writeErrors.isEmpty()) {
                return NO_ERROR_CODE;
            }
            // Since we are executing an ordered bulk write, there will be at most one BulkWriteError.
            assertTrue(writeErrors.size() == 1);
            var code = writeErrors.get(0).getCode();
            assertFalse(code == NO_ERROR_CODE);
            return code;
        } else if (runtimeException instanceof MongoException mongoException) {
            var code = mongoException.getCode();
            assertFalse(code == NO_ERROR_CODE);
            return code;
        }
        return NO_ERROR_CODE;
    }

    private static SQLSyntaxErrorException createSyntaxErrorException(
            String exceptionMessageTemplate, BsonDocument command, @Nullable Exception cause) {
        var mql = command.toJson(EXTENDED_JSON_WRITER_SETTINGS);
        return createSyntaxErrorException(exceptionMessageTemplate, mql, cause);
    }

    private static SQLSyntaxErrorException createSyntaxErrorException(
            String exceptionMessageTemplate, String mql, @Nullable Exception cause) {
        return new SQLSyntaxErrorException(
                exceptionMessageTemplate.formatted(EXCEPTION_MESSAGE_PREFIX_INVALID_MQL, mql), NULL_SQL_STATE, cause);
    }

    private static BatchUpdateException createBatchUpdateException(
            String exceptionMessage,
            int errorCode,
            MongoBulkWriteException mongoBulkWriteException,
            WriteModelsToCommandMapper writeModelsToCommandMapper) {
        var updateCounts = calculateBatchUpdateCounts(mongoBulkWriteException, writeModelsToCommandMapper);
        var batchUpdateException = new BatchUpdateException(
                exceptionMessage, NULL_SQL_STATE, errorCode, updateCounts, mongoBulkWriteException);
        return batchUpdateException;
    }

    private static int[] calculateBatchUpdateCounts(
            MongoBulkWriteException mongoBulkWriteException, WriteModelsToCommandMapper writeModelsToCommandMapper) {
        var writeErrors = mongoBulkWriteException.getWriteErrors();
        var writeConcernError = mongoBulkWriteException.getWriteConcernError();
        if (writeConcernError == null) {
            assertTrue(writeErrors.size() == 1);
            var failedModelIndex = writeErrors.get(0).getIndex();
            var failedCommandIndexInBatch = writeModelsToCommandMapper.findCommandIndex(failedModelIndex);
            return createUpdateCounts(failedCommandIndexInBatch);
        }
        return EMPTY_UPDATE_COUNTS;
    }

    private static boolean isTimeoutException(MongoException exception) {
        // We do not check for `MongoExecutionTimeoutException` and `MongoOperationTimeoutException` here,
        // because they are handled via error codes.
        return exception instanceof MongoSocketReadTimeoutException
                || exception instanceof MongoSocketWriteTimeoutException
                || exception instanceof MongoTimeoutException;
    }

    enum CommandDescription {
        /** See <a href="https://www.mongodb.com/docs/manual/reference/command/insert/">{@code insert}</a>. */
        INSERT("insert", false, true),
        /** See <a href="https://www.mongodb.com/docs/manual/reference/command/update/">{@code update}</a>. */
        UPDATE("update", false, true),
        /** See <a href="https://www.mongodb.com/docs/manual/reference/command/delete/">{@code delete}</a>. */
        DELETE("delete", false, true),
        /** See <a href="https://www.mongodb.com/docs/manual/reference/command/aggregate/">{@code aggregate}</a>. */
        AGGREGATE("aggregate", true, false);

        private final String commandName;
        private final boolean isQuery;
        private final boolean isUpdate;

        CommandDescription(String commandName, boolean isQuery, boolean isUpdate) {
            this.commandName = commandName;
            this.isQuery = isQuery;
            this.isUpdate = isUpdate;
        }

        String getCommandName() {
            return commandName;
        }

        /**
         * Indicates whether the command may be used in {@code executeUpdate(...)} or {@code executeBatch()} methods.
         *
         * @return true if the command may be used in update operations.
         */
        boolean isUpdate() {
            return isUpdate;
        }

        /**
         * Indicates whether the command may be used in {@code executeQuery(...)} methods.
         *
         * @return true if the command may be used in query operations.
         */
        boolean isQuery() {
            return isQuery;
        }

        static CommandDescription of(String commandName) throws SQLFeatureNotSupportedException {
            return switch (commandName) {
                case "insert" -> INSERT;
                case "update" -> UPDATE;
                case "delete" -> DELETE;
                case "aggregate" -> AGGREGATE;
                default -> throw new SQLFeatureNotSupportedException("Unsupported command: %s".formatted(commandName));
            };
        }
    }

    private static class WriteModelConverter {
        private static final String UNSUPPORTED_MESSAGE_TEMPLATE_STATEMENT_FIELD =
                "Unsupported field in [%s] statement: [%s]";
        private static final String UNSUPPORTED_MESSAGE_TEMPLATE_COMMAND_FIELD =
                "Unsupported field in [%s] command: [%s]";

        private static final Set<String> SUPPORTED_INSERT_COMMAND_FIELDS = Set.of("documents");

        private static final Set<String> SUPPORTED_UPDATE_COMMAND_FIELDS = Set.of("updates");
        private static final Set<String> SUPPORTED_UPDATE_STATEMENT_FIELDS = Set.of("q", "u", "multi");

        private static final Set<String> SUPPORTED_DELETE_COMMAND_FIELDS = Set.of("deletes");
        private static final Set<String> SUPPORTED_DELETE_STATEMENT_FIELDS = Set.of("q", "limit");

        private WriteModelConverter() {}

        static void convertToWriteModels(
                CommandDescription commandDescription,
                BsonDocument command,
                Collection<WriteModel<BsonDocument>> writeModels)
                throws SQLFeatureNotSupportedException, SQLSyntaxErrorException {
            try {
                switch (commandDescription) {
                    case INSERT:
                        checkCommandFields(command, commandDescription, SUPPORTED_INSERT_COMMAND_FIELDS);
                        var documentsToInsert = command.getArray("documents");
                        for (var documentToInsert : documentsToInsert) {
                            writeModels.add(createInsertModel(documentToInsert.asDocument()));
                        }
                        break;
                    case UPDATE:
                        checkCommandFields(command, commandDescription, SUPPORTED_UPDATE_COMMAND_FIELDS);
                        var updateStatements = command.getArray("updates");
                        for (var updateStatement : updateStatements) {
                            writeModels.add(createUpdateModel(updateStatement.asDocument(), commandDescription));
                        }
                        break;
                    case DELETE:
                        checkCommandFields(command, commandDescription, SUPPORTED_DELETE_COMMAND_FIELDS);
                        var deleteStatements = command.getArray("deletes");
                        for (var deleteStatement : deleteStatements) {
                            writeModels.add(createDeleteModel(deleteStatement.asDocument(), commandDescription));
                        }
                        break;
                    default:
                        throw fail(commandDescription.toString());
                }
            } catch (BSONException bsonException) {
                throw createSyntaxErrorException("%s: [%s]", command, bsonException);
            }
        }

        private static WriteModel<BsonDocument> createInsertModel(BsonDocument insertDocument) {
            return new InsertOneModel<>(insertDocument);
        }

        private static WriteModel<BsonDocument> createUpdateModel(
                BsonDocument updateStatement, CommandDescription commandDescription)
                throws SQLFeatureNotSupportedException {
            checkStatementFields(updateStatement, commandDescription, SUPPORTED_UPDATE_STATEMENT_FIELDS);
            var isMulti = updateStatement.getBoolean("multi", FALSE).getValue();
            var filter = updateStatement.getDocument("q");
            var updateModification = updateStatement.get("u");
            if (updateModification == null) {
                // We force exception here because the field is mandatory.
                updateStatement.getDocument("u");
            }
            if (!(updateModification instanceof BsonDocument updateDocument)) {
                throw new SQLFeatureNotSupportedException("Only document type is supported as value for field: [u]");
            }
            if (isMulti) {
                return new UpdateManyModel<>(filter, updateDocument);
            }
            return new UpdateOneModel<>(filter, updateDocument);
        }

        private static WriteModel<BsonDocument> createDeleteModel(
                BsonDocument deleteStatement, CommandDescription commandDescription)
                throws SQLFeatureNotSupportedException {
            checkStatementFields(deleteStatement, commandDescription, SUPPORTED_DELETE_STATEMENT_FIELDS);
            var isSingleDelete = deleteStatement.getNumber("limit").intValue() == 1;
            var filter = deleteStatement.getDocument("q");

            if (isSingleDelete) {
                return new DeleteOneModel<>(filter);
            }
            return new DeleteManyModel<>(filter);
        }

        private static void checkStatementFields(
                BsonDocument statement, CommandDescription commandDescription, Set<String> supportedStatementFields)
                throws SQLFeatureNotSupportedException {
            checkFields(
                    commandDescription,
                    UNSUPPORTED_MESSAGE_TEMPLATE_STATEMENT_FIELD,
                    supportedStatementFields,
                    statement.keySet().iterator());
        }

        private static void checkCommandFields(
                BsonDocument command, CommandDescription commandDescription, Set<String> supportedCommandFields)
                throws SQLFeatureNotSupportedException {
            var iterator = command.keySet().iterator();
            iterator.next(); // skip the command name
            checkFields(
                    commandDescription, UNSUPPORTED_MESSAGE_TEMPLATE_COMMAND_FIELD, supportedCommandFields, iterator);
        }

        private static void checkFields(
                CommandDescription commandDescription,
                String exceptionMessageTemplate,
                Set<String> supportedCommandFields,
                Iterator<String> fieldNameIterator)
                throws SQLFeatureNotSupportedException {
            while (fieldNameIterator.hasNext()) {
                var field = fieldNameIterator.next();
                if (!supportedCommandFields.contains(field)) {
                    throw new SQLFeatureNotSupportedException(
                            exceptionMessageTemplate.formatted(commandDescription.getCommandName(), field));
                }
            }
        }
    }

    /** Maps write model indices to their corresponding command indices in batch of commands. */
    private static class WriteModelsToCommandMapper {
        /** The cumulative counts of write models for each command in the batch (prefix sum). */
        private final int[] cumulativeCounts;

        private int cumulativeCountIndex;

        WriteModelsToCommandMapper(int commandCount) {
            this.cumulativeCounts = new int[commandCount];
            this.cumulativeCountIndex = 0;
        }

        void add(int cumulativeWriteModelCount) {
            assertFalse(cumulativeCountIndex >= cumulativeCounts.length);
            cumulativeCounts[cumulativeCountIndex++] = cumulativeWriteModelCount;
        }

        int findCommandIndex(int writeModelIndex) {
            assertTrue(cumulativeCountIndex == cumulativeCounts.length);
            var lo = 0;
            var hi = cumulativeCounts.length;
            while (lo < hi) {
                var mid = (lo + hi) >>> 1;
                if (cumulativeCounts[mid] >= writeModelIndex + 1) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            return lo;
        }
    }
}

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

import static com.mongodb.hibernate.internal.MongoConstants.ID_FIELD_NAME;
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toCollection;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import com.mongodb.hibernate.internal.dialect.MongoAggregateSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLWarning;
import java.util.ArrayList;
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
        MongoAggregateSupport.checkSupported(mql);
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
        MongoAggregateSupport.checkSupported(mql);
        var command = parse(mql);
        return executeUpdateCommand(command);
    }

    int executeUpdateCommand(BsonDocument command) throws SQLException {
        try {
            startTransactionIfNeeded();
            return mongoDatabase.runCommand(clientSession, command).getInteger("n");
        } catch (RuntimeException e) {
            throw new SQLException("Failed to execute update command", e);
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
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-35 https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-35 https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        closeLastOpenResultSet();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-35 https://jira.mongodb.org/browse/HIBERNATE-35");
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

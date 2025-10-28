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

import static com.mongodb.hibernate.internal.MongoAssertions.assertInstanceOf;
import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static com.mongodb.hibernate.internal.type.ValueConversions.toBsonValue;
import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import com.mongodb.hibernate.internal.type.MongoStructJdbcType;
import com.mongodb.hibernate.internal.type.ObjectIdJdbcType;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.BatchUpdateException;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.types.ObjectId;

final class MongoPreparedStatement extends MongoStatement implements PreparedStatementAdapter {

    private final BsonDocument command;
    private final List<BsonDocument> commandBatch;
    private final List<ParameterValueSetter> parameterValueSetters;

    MongoPreparedStatement(
            MongoDatabase mongoDatabase, ClientSession clientSession, MongoConnection mongoConnection, String mql)
            throws SQLSyntaxErrorException {
        super(mongoDatabase, clientSession, mongoConnection);
        command = MongoStatement.parse(mql);
        commandBatch = new ArrayList<>();
        parameterValueSetters = new ArrayList<>();
        parseParameters(command, parameterValueSetters);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkClosed();
        closeLastOpenResultSet();
        checkAllParametersSet();
        return executeQuery(command);
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkClosed();
        closeLastOpenResultSet();
        checkAllParametersSet();
        checkSupportedUpdateCommand(command);
        return executeUpdate(command);
    }

    private void checkAllParametersSet() throws SQLException {
        for (var i = 0; i < parameterValueSetters.size(); i++) {
            if (!parameterValueSetters.get(i).isUsed()) {
                throw new SQLException(format("Parameter with index [%d] is not set", i + 1));
            }
        }
        checkComparatorNotComparingWithNullValues(command);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        switch (sqlType) {
            case Types.BLOB:
            case Types.CLOB:
            case Types.DATALINK:
            case Types.NCHAR:
            case Types.NCLOB:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.REF:
            case Types.ROWID:
            case Types.SQLXML:
                throw new SQLFeatureNotSupportedException(
                        "Unsupported SQL type: " + JDBCType.valueOf(sqlType).getName());
        }
        setParameter(parameterIndex, toBsonValue((Object) null));
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        BsonValue value;
        if (targetSqlType == ObjectIdJdbcType.MQL_TYPE.getVendorTypeNumber()) {
            value = toBsonValue(assertInstanceOf(x, ObjectId.class));
        } else if (targetSqlType == MongoStructJdbcType.JDBC_TYPE.getVendorTypeNumber()) {
            value = assertInstanceOf(x, BsonDocument.class);
        } else if (targetSqlType == JDBCType.TIMESTAMP_WITH_TIMEZONE.getVendorTypeNumber()
                && x instanceof Instant instant) {
            value = toBsonValue(instant);
        } else {
            throw new SQLFeatureNotSupportedException(format(
                    "Parameter value [%s] of SQL type [%d] with index [%d] is not supported",
                    x, targetSqlType, parameterIndex));
        }
        setParameter(parameterIndex, value);
    }

    @Override
    public void addBatch() throws SQLException {
        checkClosed();
        checkAllParametersSet();
        commandBatch.add(command.clone());
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        commandBatch.clear();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        try {
            closeLastOpenResultSet();
            if (commandBatch.isEmpty()) {
                return EMPTY_BATCH_RESULT;
            }
            checkSupportedBatchCommand(commandBatch.get(0));
            executeBatch(commandBatch);
            var updateCounts = new int[commandBatch.size()];
            // We cannot determine the actual number of rows affected for each command in the batch.
            Arrays.fill(updateCounts, Statement.SUCCESS_NO_INFO);
            return updateCounts;
        } finally {
            commandBatch.clear();
        }
    }

    /** @throws BatchUpdateException if any of the commands in the batch attempts to return a result set. */
    private void checkSupportedBatchCommand(BsonDocument command) throws SQLException {
        var commandDescription = getCommandDescription(command);
        if (commandDescription.returnsResultSet()) {
            throw new BatchUpdateException(
                    format(
                            "Commands returning result set are not allowed. Received command: %s",
                            commandDescription.getCommandName()),
                    null,
                    NO_ERROR_CODE,
                    null);
        }
        if (!commandDescription.isUpdate()) {
            throw new SQLFeatureNotSupportedException(
                    format("Unsupported command for batch operation: %s", commandDescription.getCommandName()));
        }
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, toBsonValue(x));
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-55 https://jira.mongodb.org/browse/HIBERNATE-55");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("TODO-HIBERNATE-54 https://jira.mongodb.org/browse/HIBERNATE-54");
    }

    @Override
    public ResultSet executeQuery(String mql) throws SQLException {
        throw new SQLException(format("Must not be called on %s", PreparedStatement.class.getSimpleName()));
    }

    @Override
    public int executeUpdate(String mql) throws SQLException {
        throw new SQLException(format("Must not be called on %s", PreparedStatement.class.getSimpleName()));
    }

    @Override
    public boolean execute(String mql) throws SQLException {
        throw new SQLException(format("Must not be called on %s", PreparedStatement.class.getSimpleName()));
    }

    @Override
    public void addBatch(String mql) throws SQLException {
        throw new SQLException(format("Must not be called on %s", PreparedStatement.class.getSimpleName()));
    }

    private void setParameter(int parameterIndex, BsonValue parameterValue) {
        var parameterValueSetter = parameterValueSetters.get(parameterIndex - 1);
        parameterValueSetter.accept(parameterValue);
    }

    private static void parseParameters(BsonDocument command, List<ParameterValueSetter> parameterValueSetters) {
        for (var entry : command.entrySet()) {
            if (isParameterMarker(entry.getValue())) {
                parameterValueSetters.add(new ParameterValueSetter(entry::setValue));
            } else if (entry.getValue().getBsonType().isContainer()) {
                parseParameters(entry.getValue(), parameterValueSetters);
            }
        }
    }

    private static void parseParameters(BsonArray array, List<ParameterValueSetter> parameterValueSetters) {
        for (var i = 0; i < array.size(); i++) {
            var value = array.get(i);
            if (isParameterMarker(value)) {
                var idx = i;
                parameterValueSetters.add(new ParameterValueSetter(v -> array.set(idx, v)));
            } else if (value.getBsonType().isContainer()) {
                parseParameters(value, parameterValueSetters);
            }
        }
    }

    private static void parseParameters(BsonValue value, List<ParameterValueSetter> parameterValueSetters) {
        if (value instanceof BsonDocument document) {
            parseParameters(document, parameterValueSetters);
        } else if (value instanceof BsonArray array) {
            parseParameters(array, parameterValueSetters);
        } else {
            fail("Only BSON container type (BsonDocument or BsonArray) is accepted; provided type: "
                    + value.getBsonType());
        }
    }

    private static boolean isParameterMarker(BsonValue value) {
        return value.getBsonType() == BsonType.UNDEFINED;
    }

    private void checkParameterIndex(int parameterIndex) throws SQLException {
        if (parameterValueSetters.isEmpty()) {
            throw new SQLException("No parameter exists");
        }
        if (parameterIndex < 1 || parameterIndex > parameterValueSetters.size()) {
            throw new SQLException(format(
                    "Invalid parameter index [%d]; cannot be under 1 or over the current number of parameters [%d]",
                    parameterIndex, parameterValueSetters.size()));
        }
    }

    private static final class ParameterValueSetter implements Consumer<BsonValue> {
        private final Consumer<BsonValue> setter;
        private boolean used;

        ParameterValueSetter(Consumer<BsonValue> setter) {
            this.setter = setter;
        }

        @Override
        public void accept(BsonValue bsonValue) {
            used = true;
            setter.accept(bsonValue);
        }

        boolean isUsed() {
            return used;
        }
    }

    /**
     * Temporary method to ensure exception is thrown when comparison query operators are comparing with {@code null}
     * values.
     *
     * <p>Note that only find expression is involved before HIBERNATE-74. TODO-HIBERNATE-74 delete this temporary method
     */
    private static void checkComparatorNotComparingWithNullValues(BsonDocument document) {
        var comparisonOperators = Set.of("$ne", "$gt", "$gte", "$lt", "$lte", "$in", "$nin");
        for (var entry : document.entrySet()) {
            var value = entry.getValue();
            if (value.isNull() && comparisonOperators.contains(entry.getKey())) {
                throw new FeatureNotSupportedException(
                        "TODO-HIBERNATE-74 https://jira.mongodb.org/browse/HIBERNATE-74");
            }
            if (value instanceof BsonDocument documentValue) {
                checkComparatorNotComparingWithNullValues(documentValue);
            } else if (value instanceof BsonArray arrayValue) {
                for (var element : arrayValue) {
                    if (element instanceof BsonDocument documentElement) {
                        checkComparatorNotComparingWithNullValues(documentElement);
                    }
                }
            }
        }
    }
}

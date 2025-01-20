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
import static com.mongodb.hibernate.internal.VisibleForTesting.AccessModifier.PRIVATE;
import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import com.mongodb.hibernate.internal.VisibleForTesting;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDateTime;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonType;
import org.bson.BsonValue;
import org.bson.types.Decimal128;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@link java.sql.PreparedStatement} implementation class.
 *
 * <p>It only focuses on API methods MongoDB Dialect will support. All the other methods are implemented by throwing
 * exceptions in its parent {@link PreparedStatementAdapter adapter interface}.
 */
final class MongoPreparedStatement extends MongoStatement implements PreparedStatementAdapter {

    private final BsonDocument command;

    private final List<Consumer<BsonValue>> parameterValueSetters;

    public MongoPreparedStatement(
            MongoClient mongoClient, ClientSession clientSession, MongoConnection mongoConnection, String mql) {
        super(mongoClient, clientSession, mongoConnection);
        this.command = BsonDocument.parse(mql);
        this.parameterValueSetters = new ArrayList<>();
        parseParameters(command, parameterValueSetters);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public int executeUpdate() throws SQLException {
        checkClosed();
        return executeUpdateCommand(command);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        checkClosed();
        checkSqlTypeSupported(sqlType);
        setParameter(parameterIndex, BsonNull.VALUE);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        setParameter(parameterIndex, BsonBoolean.valueOf(x));
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        setInt(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        setInt(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        setParameter(parameterIndex, new BsonInt32(x));
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        setParameter(parameterIndex, new BsonInt64(x));
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        setDouble(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        setParameter(parameterIndex, new BsonDouble(x));
    }

    @Override
    public void setBigDecimal(int parameterIndex, @Nullable BigDecimal x) throws SQLException {
        setParameter(parameterIndex, x == null ? BsonNull.VALUE : new BsonDecimal128(new Decimal128(x)));
    }

    @Override
    public void setString(int parameterIndex, @Nullable String x) throws SQLException {
        setParameter(parameterIndex, x == null ? BsonNull.VALUE : new BsonString(x));
    }

    @Override
    public void setBytes(int parameterIndex, byte @Nullable [] x) throws SQLException {
        setParameter(parameterIndex, x == null ? BsonNull.VALUE : new BsonBinary(x));
    }

    @Override
    public void setDate(int parameterIndex, @Nullable Date x) throws SQLException {
        setBsonDateTimeParameter(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, @Nullable Time x) throws SQLException {
        setBsonDateTimeParameter(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, @Nullable Timestamp x) throws SQLException {
        setBsonDateTimeParameter(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, @Nullable InputStream x, int length) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    // ----------------------------------------------------------------------
    // Advanced features:

    @Override
    public void setObject(int parameterIndex, @Nullable Object x, int targetSqlType) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("To be implemented during Array / Struct tickets");
    }

    @Override
    public void setObject(int parameterIndex, @Nullable Object x) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException("To be implemented during Array / Struct tickets");
    }

    // --------------------------JDBC 2.0-----------------------------

    @Override
    public void addBatch() throws SQLException {
        checkClosed();
        throw new NotYetImplementedException(
                "To be implemented in scope of https://jira.mongodb.org/browse/HIBERNATE-35");
    }

    @Override
    public void setBlob(int parameterIndex, @Nullable Blob x) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public void setClob(int parameterIndex, @Nullable Clob x) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public void setArray(int parameterIndex, @Nullable Array x) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public void setDate(int parameterIndex, @Nullable Date x, @Nullable Calendar cal) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public void setTime(int parameterIndex, @Nullable Time x, @Nullable Calendar cal) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public void setTimestamp(int parameterIndex, @Nullable Timestamp x, @Nullable Calendar cal) throws SQLException {
        checkClosed();
        throw new NotYetImplementedException();
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, @Nullable String typeName) throws SQLException {
        checkClosed();
        checkSqlTypeSupported(sqlType);
        setParameter(parameterIndex, BsonNull.VALUE);
    }

    @SuppressWarnings("JavaUtilDate")
    private void setBsonDateTimeParameter(int parameterIndex, java.util.@Nullable Date date) throws SQLException {
        setParameter(parameterIndex, date == null ? BsonNull.VALUE : new BsonDateTime(date.getTime()));
    }

    private void setParameter(int parameterIndex, BsonValue parameterValue) throws SQLException {
        checkClosed();
        if (parameterIndex < 1 || parameterIndex > parameterValueSetters.size()) {
            throw new SQLException(format(
                    "Parameter index invalid: %d; should be within [1, %d]",
                    parameterIndex, parameterValueSetters.size()));
        }
        var parameterValueSetter = parameterValueSetters.get(parameterIndex - 1);
        parameterValueSetter.accept(parameterValue);
    }

    private static void parseParameters(BsonDocument command, List<Consumer<BsonValue>> parameters) {
        for (var entry : command.entrySet()) {
            if (isParameterMarker(entry.getValue())) {
                parameters.add(entry::setValue);
            } else if (entry.getValue().getBsonType().isContainer()) {
                parseParameters(entry.getValue(), parameters);
            }
        }
    }

    private static void parseParameters(BsonArray array, List<Consumer<BsonValue>> parameters) {
        for (var i = 0; i < array.size(); i++) {
            var value = array.get(i);
            if (isParameterMarker(value)) {
                var idx = i;
                parameters.add(v -> array.set(idx, v));
            } else if (value.getBsonType().isContainer()) {
                parseParameters(value, parameters);
            }
        }
    }

    private static void parseParameters(BsonValue value, List<Consumer<BsonValue>> parameters) {
        if (value.isDocument()) {
            parseParameters(value.asDocument(), parameters);
        } else if (value.isArray()) {
            parseParameters(value.asArray(), parameters);
        } else {
            fail();
        }
    }

    private static boolean isParameterMarker(BsonValue value) {
        return value.getBsonType() == BsonType.UNDEFINED;
    }

    @VisibleForTesting(otherwise = PRIVATE)
    List<@Nullable Consumer<BsonValue>> getParameterValueSetters() {
        return parameterValueSetters;
    }

    private void checkSqlTypeSupported(int sqlType) throws SQLFeatureNotSupportedException {
        switch (sqlType) {
            case Types.BOOLEAN:
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.REAL:
            case Types.DOUBLE:
            case Types.NUMERIC:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.BINARY:
            case Types.LONGVARBINARY:
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                break;
            default:
                throw new SQLFeatureNotSupportedException(
                        "Unsupported sql type: " + JDBCType.valueOf(sqlType).getName());
        }
    }
}

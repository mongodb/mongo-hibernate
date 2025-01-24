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

import static com.mongodb.hibernate.internal.MongoAssertions.fail;
import static java.lang.String.format;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Array;
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
        checkParameterIndex(parameterIndex);
        switch (sqlType) {
            case Types.ARRAY:
            case Types.BLOB:
            case Types.CLOB:
            case Types.DATALINK:
            case Types.JAVA_OBJECT:
            case Types.NCHAR:
            case Types.NCLOB:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.REF:
            case Types.ROWID:
            case Types.SQLXML:
            case Types.STRUCT:
                throw new SQLFeatureNotSupportedException(
                        "Unsupported sql type: " + JDBCType.valueOf(sqlType).getName());
        }
        setParameter(parameterIndex, BsonNull.VALUE);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, BsonBoolean.valueOf(x));
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setInt(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setInt(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, new BsonInt32(x));
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, new BsonInt64(x));
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setDouble(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setParameter(parameterIndex, new BsonDouble(x));
    }

    @Override
    public void setBigDecimal(int parameterIndex, @Nullable BigDecimal x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        if (x == null) {
            setNull(parameterIndex, Types.NUMERIC);
        } else {
            setParameter(parameterIndex, new BsonDecimal128(new Decimal128(x)));
        }
    }

    @Override
    public void setString(int parameterIndex, @Nullable String x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        if (x == null) {
            setNull(parameterIndex, Types.VARCHAR);
        } else {
            setParameter(parameterIndex, new BsonString(x));
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte @Nullable [] x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        if (x == null) {
            setNull(parameterIndex, Types.VARBINARY);
        } else {
            setParameter(parameterIndex, new BsonBinary(x));
        }
    }

    @Override
    public void setDate(int parameterIndex, @Nullable Date x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setDate(parameterIndex, x, null);
    }

    @Override
    public void setTime(int parameterIndex, @Nullable Time x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setTime(parameterIndex, x, null);
    }

    @Override
    public void setTimestamp(int parameterIndex, @Nullable Timestamp x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        setTimestamp(parameterIndex, x, null);
    }

    @Override
    public void setBinaryStream(int parameterIndex, @Nullable InputStream x, int length) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException();
    }

    // ----------------------------------------------------------------------
    // Advanced features:

    @Override
    public void setObject(int parameterIndex, @Nullable Object x, int targetSqlType) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException("To be implemented during Array / Struct tickets");
    }

    @Override
    public void setObject(int parameterIndex, @Nullable Object x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
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
    public void setArray(int parameterIndex, @Nullable Array x) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException();
    }

    @Override
    public void setDate(int parameterIndex, @Nullable Date x, @Nullable Calendar cal) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException("To implement in scope of https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public void setTime(int parameterIndex, @Nullable Time x, @Nullable Calendar cal) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException("To implement in scope of https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public void setTimestamp(int parameterIndex, @Nullable Timestamp x, @Nullable Calendar cal) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException("To implement in scope of https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, @Nullable String typeName) throws SQLException {
        checkClosed();
        checkParameterIndex(parameterIndex);
        throw new NotYetImplementedException("To be implemented during Array / Struct tickets");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }

    private void setParameter(int parameterIndex, BsonValue parameterValue) {
        var parameterValueSetter = parameterValueSetters.get(parameterIndex - 1);
        parameterValueSetter.accept(parameterValue);
    }

    private static void parseParameters(BsonDocument command, List<Consumer<BsonValue>> parameterValueSetters) {
        for (var entry : command.entrySet()) {
            if (isParameterMarker(entry.getValue())) {
                parameterValueSetters.add(entry::setValue);
            } else if (entry.getValue().getBsonType().isContainer()) {
                parseParameters(entry.getValue(), parameterValueSetters);
            }
        }
    }

    private static void parseParameters(BsonArray array, List<Consumer<BsonValue>> parameterValueSetters) {
        for (var i = 0; i < array.size(); i++) {
            var value = array.get(i);
            if (isParameterMarker(value)) {
                var idx = i;
                parameterValueSetters.add(v -> array.set(idx, v));
            } else if (value.getBsonType().isContainer()) {
                parseParameters(value, parameterValueSetters);
            }
        }
    }

    private static void parseParameters(BsonValue value, List<Consumer<BsonValue>> parameterValueSetters) {
        if (value.isDocument()) {
            parseParameters(value.asDocument(), parameterValueSetters);
        } else if (value.isArray()) {
            parseParameters(value.asArray(), parameterValueSetters);
        } else {
            fail("Only BSON container type (BsonDocument or BsonArray) is accepted");
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
                    "Parameter index invalid: %d; should be within [1, %d]",
                    parameterIndex, parameterValueSetters.size()));
        }
    }
}

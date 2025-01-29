/*
 * Copyright 2025-present MongoDB, Inc.
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

/*
 * Copyright 2025-present MongoDB, Inc.
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

import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static java.lang.String.format;

import com.mongodb.client.MongoCursor;
import com.mongodb.hibernate.internal.NotYetImplementedException;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

/**
 * MongoDB Dialect's JDBC {@link java.sql.ResultSet} implementation class.
 *
 * <p>It only focuses on API methods Mongo Dialect will support. All the other methods are implemented by throwing
 * exceptions in its parent {@link ResultSetAdapter adapter interface}.
 */
final class MongoResultSet implements ResultSetAdapter {

    private class MongoResultSetMetadata implements ResultSetMetaDataAdapter {
        @Override
        public int getColumnCount() {
            return fieldNames.size();
        }

        @Override
        public String getColumnLabel(final int column) {
            return fieldNames.get(column - 1);
        }
    }

    private final MongoCursor<BsonDocument> mongoCursor;

    private final List<String> fieldNames;

    private @Nullable BsonDocument currentDocument;

    private @Nullable BsonValue lastReadColumnValue;

    private boolean closed;

    MongoResultSet(MongoCursor<BsonDocument> mongoCursor, List<String> fieldNames) {
        this.mongoCursor = mongoCursor;
        this.fieldNames = fieldNames;
    }

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        if (mongoCursor.hasNext()) {
            currentDocument = mongoCursor.next().toBsonDocument();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            try {
                mongoCursor.close();
            } catch (RuntimeException e) {
                throw new SQLException("Failed to close MongoCursor: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean wasNull() throws SQLException {
        checkClosed();
        return assertNotNull(lastReadColumnValue).isNull();
    }

    @Override
    public @Nullable String getString(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull() ? null : bsonValue.asString().getValue();
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return !bsonValue.isNull() && bsonValue.asBoolean().getValue();
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return (byte) (bsonValue.isNull() ? 0 : bsonValue.asNumber().intValue());
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return (short) (bsonValue.isNull() ? 0 : bsonValue.asNumber().intValue());
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull() ? 0 : bsonValue.asNumber().intValue();
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull() ? 0 : bsonValue.asNumber().longValue();
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull() ? 0 : (float) bsonValue.asNumber().doubleValue();
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull() ? 0 : bsonValue.asNumber().doubleValue();
    }

    @Override
    public byte @Nullable [] getBytes(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull() ? null : bsonValue.asBinary().getData();
    }

    @Override
    public @Nullable Date getDate(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Time getTime(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Time getTime(int columnIndex, Calendar cal) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        BsonValue bsonValue = getBsonValue(columnIndex);
        return bsonValue.isNull()
                ? null
                : bsonValue.asDecimal128().decimal128Value().bigDecimalValue();
    }

    @Override
    public @Nullable Array getArray(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException();
    }

    @Override
    public <T> @Nullable T getObject(int columnIndex, Class<T> type) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new NotYetImplementedException("In scope of Array / Struct implementation");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new MongoResultSetMetadata();
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkClosed();
        var index = fieldNames.indexOf(columnLabel);
        if (index < 0) {
            throw new SQLException("Unknown column label: " + columnLabel);
        }
        return index + 1;
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        checkClosed();
        return false;
    }

    private String getKey(int columnIndex) {
        return fieldNames.get(columnIndex - 1);
    }

    private void checkClosed() throws SQLException {
        if (closed) {
            throw new SQLException("MongoResultSet has been closed");
        }
    }

    private BsonValue getBsonValue(int columnIndex) throws SQLException {
        lastReadColumnValue = assertNotNull(currentDocument).get(getKey(columnIndex), BsonNull.VALUE);
        return lastReadColumnValue;
    }

    private void checkColumnIndex(int columnIndex) throws SQLException {
        if (fieldNames.isEmpty()) {
            throw new SQLException("No field exists");
        }
        if (columnIndex < 1 || columnIndex > fieldNames.size()) {
            throw new SQLException(
                    format("Invalid column index [%d]; should be within [1, %d]", columnIndex, fieldNames.size()));
        }
    }
}

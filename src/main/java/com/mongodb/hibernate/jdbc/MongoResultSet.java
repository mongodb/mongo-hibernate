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

import static com.mongodb.hibernate.internal.MongoAssertions.assertFalse;
import static com.mongodb.hibernate.internal.MongoAssertions.assertNotNull;
import static com.mongodb.hibernate.jdbc.MongoPreparedStatement.checkTimeZone;
import static java.lang.String.format;

import com.mongodb.client.MongoCursor;
import com.mongodb.hibernate.internal.type.ValueConversions;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.types.ObjectId;
import org.jspecify.annotations.Nullable;

final class MongoResultSet implements ResultSetAdapter {

    private final MongoCursor<BsonDocument> mongoCursor;

    private final List<String> fieldNames;

    private @Nullable BsonDocument currentDocument;

    private boolean lastReadColumnValueWasNull;

    private boolean closed;

    MongoResultSet(MongoCursor<BsonDocument> mongoCursor, List<String> fieldNames) {
        assertFalse(fieldNames.isEmpty());
        this.mongoCursor = mongoCursor;
        this.fieldNames = fieldNames;
    }

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        if (mongoCursor.hasNext()) {
            currentDocument = mongoCursor.next();
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
                throw new SQLException(
                        format("Failed to close %s", mongoCursor.getClass().getSimpleName()), e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean wasNull() throws SQLException {
        checkClosed();
        return lastReadColumnValueWasNull;
    }

    @Override
    public @Nullable String getString(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toStringDomainValue);
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toBooleanDomainValue, false);
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toIntDomainValue, 0);
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toLongDomainValue, 0L);
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toDoubleDomainValue, 0d);
    }

    @Override
    public byte @Nullable [] getBytes(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toByteArrayDomainValue);
    }

    @Override
    public Date getDate(final int columnIndex, final Calendar cal) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new SQLFeatureNotSupportedException("Date type is not supported");
    }

    @Override
    public @Nullable Date getDate(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new SQLFeatureNotSupportedException("Date type is not supported");
    }

    @Override
    public @Nullable Time getTime(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new SQLFeatureNotSupportedException("Time type is not supported");
    }

    @Override
    public @Nullable Time getTime(int columnIndex, Calendar cal) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new SQLFeatureNotSupportedException("Time type is not supported");
    }

    @Override
    public @Nullable Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new SQLFeatureNotSupportedException("Timestamp type with default calendar is not supported");
    }

    @Override
    public @Nullable Timestamp getTimestamp(int columnIndex, Calendar calendar) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        checkTimeZone(calendar.getTimeZone());
        return getValue(columnIndex, ValueConversions::toTimestampDomainValue);
    }

    @Override
    public @Nullable BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toBigDecimalDomainValue);
    }

    @Override
    public @Nullable Array getArray(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, ValueConversions::toArrayDomainValue);
    }

    @Override
    public <T> @Nullable T getObject(int columnIndex, Class<T> type) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        Object value;
        if (type.equals(ObjectId.class)) {
            value = getValue(columnIndex, ValueConversions::toObjectIdDomainValue);
        } else if (type.equals(BsonDocument.class)) {
            value = getValue(columnIndex, ValueConversions::toBsonDocumentDomainValue);
        } else {
            throw new SQLFeatureNotSupportedException(
                    format("Type [%s] for a column with index [%d] is not supported", type, columnIndex));
        }
        return type.cast(value);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        return new MongoResultSetMetadata();
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkClosed();
        throw new SQLFeatureNotSupportedException("To be implemented in scope of native query tickets");
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
            throw new SQLException(format("%s has been closed", getClass().getSimpleName()));
        }
    }

    private <T> T getValue(int columnIndex, SqlFunction<BsonValue, T> toJavaConverter, T defaultValue)
            throws SQLException {
        return Objects.requireNonNullElse(getValue(columnIndex, toJavaConverter), defaultValue);
    }

    private <T> @Nullable T getValue(int columnIndex, SqlFunction<BsonValue, T> toJavaConverter) throws SQLException {
        try {
            var key = getKey(columnIndex);
            var bsonValue = assertNotNull(currentDocument).get(key);
            T value = ValueConversions.isNull(bsonValue) ? null : toJavaConverter.apply(assertNotNull(bsonValue));
            lastReadColumnValueWasNull = value == null;
            return value;
        } catch (RuntimeException e) {
            throw new SQLException(format("Failed to get value from column [index: %d]", columnIndex), e);
        }
    }

    private void checkColumnIndex(int columnIndex) throws SQLException {
        if (columnIndex < 1 || columnIndex > fieldNames.size()) {
            throw new SQLException(format(
                    "Invalid column index [%d]; cannot be under 1 or over the current number of fields [%d]",
                    columnIndex, fieldNames.size()));
        }
    }

    private static final class MongoResultSetMetadata implements ResultSetMetaDataAdapter {}

    private interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }
}

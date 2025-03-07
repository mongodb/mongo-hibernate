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
import com.mongodb.hibernate.internal.FeatureNotSupportedException;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.bson.BsonNull;
import org.bson.BsonValue;
import org.jspecify.annotations.Nullable;

final class MongoResultSet implements ResultSetAdapter {

    private final MongoCursor<BsonDocument> mongoCursor;

    private final List<String> fieldNames;

    private @Nullable BsonDocument currentDocument;

    private boolean lastReadColumnValueWasNull;

    private boolean closed;

    MongoResultSet(MongoCursor<BsonDocument> mongoCursor, List<String> fieldNames) {
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
        return getValue(columnIndex, bsonValue -> bsonValue.asString().getValue());
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, bsonValue -> bsonValue.asBoolean().getValue(), false);
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, bsonValue -> bsonValue.asInt32().intValue(), 0);
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, bsonValue -> bsonValue.asInt64().longValue(), 0L);
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, bsonValue -> bsonValue.asDouble().getValue(), 0)
                .doubleValue();
    }

    @Override
    public byte @Nullable [] getBytes(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(columnIndex, bsonValue -> bsonValue.asBinary().getData());
    }

    @Override
    public @Nullable Date getDate(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Time getTime(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Time getTime(int columnIndex, Calendar cal) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Timestamp getTimestamp(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException("TODO-HIBERNATE-42 https://jira.mongodb.org/browse/HIBERNATE-42");
    }

    @Override
    public @Nullable BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        return getValue(
                columnIndex,
                bsonValue -> bsonValue.asDecimal128().decimal128Value().bigDecimalValue());
    }

    @Override
    public @Nullable Array getArray(int columnIndex) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException();
    }

    @Override
    public <T> @Nullable T getObject(int columnIndex, Class<T> type) throws SQLException {
        checkClosed();
        checkColumnIndex(columnIndex);
        throw new FeatureNotSupportedException("To be implemented in scope of Array / Struct tickets");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("To be implemented in scope of native query tickets");
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkClosed();
        throw new FeatureNotSupportedException("To be implemented in scope of native query tickets");
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

    private <T> T getValue(int columnIndex, Function<BsonValue, T> toJavaConverter, T defaultValue)
            throws SQLException {
        return Objects.requireNonNullElse(getValue(columnIndex, toJavaConverter), defaultValue);
    }

    private <T> @Nullable T getValue(int columnIndex, Function<BsonValue, T> toJavaConverter) throws SQLException {
        try {
            var bsonValue = assertNotNull(currentDocument).get(getKey(columnIndex), BsonNull.VALUE);
            lastReadColumnValueWasNull = bsonValue.isNull();
            if (bsonValue.isNull()) {
                return null;
            }
            return toJavaConverter.apply(bsonValue);
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
}

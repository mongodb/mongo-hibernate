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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.mongodb.client.MongoCursor;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoResultSetTests {

    private static final List<String> FIELDS = List.of("id", "title", "publishYear");

    @Mock
    private MongoCursor<BsonDocument> mongoCursor;

    @AutoClose
    private MongoResultSet mongoResultSet;

    @BeforeEach
    void beforeEach() {
        mongoResultSet = new MongoResultSet(mongoCursor, FIELDS);
    }

    @Test
    void testColumnIndexUnderflow() {
        checkGetterMethods(0, this::assertThrowsOutOfRangeException);
    }

    @Test
    void testColumnIndexOverflow() {
        checkGetterMethods(FIELDS.size() + 1, this::assertThrowsOutOfRangeException);
    }

    @Test
    void testCheckClosed() throws SQLException {
        mongoResultSet.close();
        checkMethodsWithOpenPrecondition(this::assertThrowsClosedException);
    }

    @Nested
    class GettersTests {

        private void createResultSetWith(BsonValue value) throws SQLException {
            var bsonDocument = new BsonDocument().append("field", value);

            doReturn(true).when(mongoCursor).hasNext();
            doReturn(bsonDocument).when(mongoCursor).next();
            mongoResultSet = new MongoResultSet(mongoCursor, singletonList("field"));
            assertTrue(mongoResultSet.next());
        }

        @Test
        void testGettersForNull() throws SQLException {
            createResultSetWith(new BsonNull());
            assertAll(
                    () -> assertNull(mongoResultSet.getString(1)),
                    () -> assertFalse(mongoResultSet.getBoolean(1)),
                    () -> assertEquals(0, mongoResultSet.getInt(1)),
                    () -> assertEquals(0L, mongoResultSet.getLong(1)),
                    () -> assertEquals(0D, mongoResultSet.getDouble(1)),
                    () -> assertNull(mongoResultSet.getBigDecimal(1)));
        }

        @Test
        void testGettersForBoolean() throws SQLException {
            createResultSetWith(new BsonBoolean(true));
            assertAll(
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getString(1)),
                    () -> assertTrue(mongoResultSet.getBoolean(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getInt(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getLong(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getDouble(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBigDecimal(1)));
        }

        @Test
        void testGettersForDouble() throws SQLException {
            createResultSetWith(new BsonDouble(3.1415));
            assertAll(
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getString(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBoolean(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getInt(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getLong(1)),
                    () -> assertEquals(3.1415, mongoResultSet.getDouble(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBigDecimal(1)));
        }

        @Test
        void testGettersForInt() throws SQLException {
            createResultSetWith(new BsonInt32(120));
            assertAll(
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getString(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBoolean(1)),
                    () -> assertEquals(120, mongoResultSet.getInt(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getLong(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getDouble(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBigDecimal(1)));
        }

        @Test
        void testGettersForLong() throws SQLException {
            createResultSetWith(new BsonInt64(12345678));
            assertAll(
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getString(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBoolean(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getInt(1)),
                    () -> assertEquals(12345678L, mongoResultSet.getLong(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getDouble(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBigDecimal(1)));
        }

        @Test
        void testGettersForString() throws SQLException {
            createResultSetWith(new BsonString("Hello World"));
            assertAll(
                    () -> assertEquals("Hello World", mongoResultSet.getString(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBoolean(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getInt(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getLong(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getDouble(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBigDecimal(1)));
        }

        @Test
        void testGettersForBigDecimal() throws SQLException {
            var bigDecimalValue = new BigDecimal("10692467440017.111");
            var value = new BsonDecimal128(new Decimal128(bigDecimalValue));
            createResultSetWith(value);
            assertAll(
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getString(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getBoolean(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getInt(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getLong(1)),
                    () -> assertThrowsTypeMismatchException(() -> mongoResultSet.getDouble(1)),
                    () -> assertEquals(bigDecimalValue, mongoResultSet.getBigDecimal(1)));
        }
    }

    private void checkMethodsWithOpenPrecondition(Consumer<Executable> asserter) {
        checkGetterMethods(1, asserter);
        assertAll(
                () -> asserter.accept(() -> mongoResultSet.next()),
                () -> asserter.accept(() -> mongoResultSet.wasNull()),
                () -> asserter.accept(() -> mongoResultSet.getMetaData()),
                () -> asserter.accept(() -> mongoResultSet.findColumn("id")),
                () -> asserter.accept(() -> mongoResultSet.isWrapperFor(MongoResultSet.class)));
    }

    private void checkGetterMethods(int columnIndex, Consumer<Executable> asserter) {
        assertAll(
                () -> asserter.accept(() -> mongoResultSet.getString(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getBoolean(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getInt(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getLong(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getDouble(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getBytes(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getDate(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getTime(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getTime(columnIndex, Calendar.getInstance())),
                () -> asserter.accept(() -> mongoResultSet.getTimestamp(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getTimestamp(columnIndex, Calendar.getInstance())),
                () -> asserter.accept(() -> mongoResultSet.getBigDecimal(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getArray(columnIndex)),
                () -> asserter.accept(() -> mongoResultSet.getObject(columnIndex, UUID.class)));
    }

    private void assertThrowsOutOfRangeException(Executable executable) {
        var e = assertThrows(SQLException.class, executable);
        assertThat(e.getMessage())
                .matches(
                        "Invalid column index \\[\\d+]; cannot be under 1 or over the current number of fields \\[\\d+]");
    }

    private void assertThrowsClosedException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertEquals("MongoResultSet has been closed", exception.getMessage());
    }

    private void assertThrowsTypeMismatchException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertThat(exception.getMessage())
                .matches(
                        "Failed to get value from column \\[index: \\d+]: Value expected to be of type \\S+ is of unexpected type \\S+");
    }
}

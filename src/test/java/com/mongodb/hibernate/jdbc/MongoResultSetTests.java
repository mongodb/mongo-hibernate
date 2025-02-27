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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.mongodb.client.MongoCursor;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoResultSetTests {
    private static final List<String> FIELDS = List.of("id", "title", "author");

    @Mock
    private MongoCursor<BsonDocument> mongoCursor;

    private MongoResultSet mongoResultSet;

    @BeforeEach
    void beforeEach() {
        mongoResultSet = new MongoResultSet(mongoCursor, FIELDS);
    }

    @Nested
    class ClosedTests {

        @Test
        void testIsIdempotent() throws SQLException {
            mongoResultSet.close();

            assertDoesNotThrow(() -> mongoResultSet.close());
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoResultSet")
        @MethodSource("getMongoResultSetMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, ResultSetMethodInvocation methodInvocation) throws SQLException {
            mongoResultSet.close();

            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertEquals("MongoResultSet has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsImpactedByClosing() {
            return getOpenPreconditionInvocations();
        }
    }

    @Nested
    class ColumnIndexCheckingTests {

        @ParameterizedTest(
                name = "SQLException is thrown when \"{0}\" is called on a MongoResultSet with columnIndex too low")
        @MethodSource("getMongoResultSetMethodInvocationsWithColumnIndexUnderflow")
        void testColumnIndexUnderflow(String label, ResultSetMethodInvocation methodInvocation) {
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertTrue(exception.getMessage().startsWith("Invalid column index"));
        }

        @ParameterizedTest(
                name = "SQLException is thrown when \"{0}\" is called on a MongoResultSet with columnIndex too high")
        @MethodSource("getMongoResultSetMethodInvocationsWithColumnIndexOverflow")
        void testColumnIndexOverflow(String label, ResultSetMethodInvocation methodInvocation) {
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertTrue(exception.getMessage().startsWith("Invalid column index"));
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsWithColumnIndexUnderflow() {
            return getGetterInvocations(0);
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsWithColumnIndexOverflow() {
            return getGetterInvocations(FIELDS.size() + 1);
        }
    }

    static Stream<Arguments> getOpenPreconditionInvocations() {
        var invocationsImpactedByColumnIndex = getGetterInvocations(1);
        var additionalArguments = Map.<String, ResultSetMethodInvocation>ofEntries(
                        Map.entry("next()", ResultSet::next),
                        Map.entry("wasNull()", ResultSet::wasNull),
                        Map.entry("getMetaData()", ResultSet::getMetaData),
                        Map.entry("findColumn(String)", rs -> rs.findColumn("name")),
                        Map.entry("isWrapperFor(Class)", rs -> rs.isWrapperFor(MongoResultSet.class)))
                .entrySet()
                .stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        return Stream.concat(invocationsImpactedByColumnIndex, additionalArguments);
    }

    static Stream<Arguments> getGetterInvocations(int columnIndex) {
        return Map.<String, ResultSetMethodInvocation>ofEntries(
                        Map.entry("getString(int)", rs -> rs.getString(columnIndex)),
                        Map.entry("getBoolean(int)", rs -> rs.getBoolean(columnIndex)),
                        Map.entry("getByte(int)", rs -> rs.getByte(columnIndex)),
                        Map.entry("getShort(int)", rs -> rs.getShort(columnIndex)),
                        Map.entry("getInt(int)", rs -> rs.getInt(columnIndex)),
                        Map.entry("getLong(int)", rs -> rs.getLong(columnIndex)),
                        Map.entry("getFloat(int)", rs -> rs.getFloat(columnIndex)),
                        Map.entry("getDouble(int)", rs -> rs.getDouble(columnIndex)),
                        Map.entry("getBytes(int)", rs -> rs.getBytes(columnIndex)),
                        Map.entry("getDate(int)", rs -> rs.getDate(columnIndex)),
                        Map.entry("getTime(int)", rs -> rs.getTime(columnIndex)),
                        Map.entry("getTime(int,Calendar)", rs -> rs.getTime(columnIndex, Calendar.getInstance())),
                        Map.entry("getTimestamp(int)", rs -> rs.getTimestamp(columnIndex)),
                        Map.entry(
                                "getTimestamp(int,Calendar)",
                                rs -> rs.getTimestamp(columnIndex, Calendar.getInstance())),
                        Map.entry("getBigDecimal(int)", rs -> rs.getBigDecimal(columnIndex)),
                        Map.entry("getArray(int)", rs -> rs.getArray(columnIndex)),
                        Map.entry("getObject(int,Class)", rs -> rs.getObject(columnIndex, String.class)))
                .entrySet()
                .stream()
                .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    interface ResultSetMethodInvocation {
        void runOn(ResultSet rs) throws SQLException;
    }

    @Nested
    class GetValueTests {

        private MongoResultSet createResultSetWith(BsonValue value) throws SQLException {
            var bsonDocument = new BsonDocument().append("field", value);

            doReturn(true).when(mongoCursor).hasNext();
            doReturn(bsonDocument).when(mongoCursor).next();
            mongoResultSet = new MongoResultSet(mongoCursor, singletonList("field"));
            assertTrue(mongoResultSet.next());
            return mongoResultSet;
        }

        @Test
        void testGettersForNull() throws SQLException {
            var value = new BsonNull();
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertNull(mongoResultSet.getString(1)),
                        () -> assertFalse(mongoResultSet.getBoolean(1)),
                        () -> assertEquals((byte) 0, mongoResultSet.getByte(1)),
                        () -> assertEquals((short) 0, mongoResultSet.getShort(1)),
                        () -> assertEquals(0, mongoResultSet.getInt(1)),
                        () -> assertEquals(0L, mongoResultSet.getLong(1)),
                        () -> assertEquals(0F, mongoResultSet.getFloat(1)),
                        () -> assertEquals(0D, mongoResultSet.getDouble(1)),
                        () -> assertNull(mongoResultSet.getBigDecimal(1)));
            }
        }

        @Test
        void testGettersForBoolean() throws SQLException {
            var value = new BsonBoolean(true);
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(1)),
                        () -> assertTrue(mongoResultSet.getBoolean(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(1)));
            }
        }

        @Test
        void testGettersForDouble() throws SQLException {
            var value = new BsonDouble(3.1415);
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(1)),
                        () -> assertEquals(3.1415F, mongoResultSet.getFloat(1)),
                        () -> assertEquals(3.1415, mongoResultSet.getDouble(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(1)));
            }
        }

        @Test
        void testGettersForInt() throws SQLException {
            var value = new BsonInt32(120);
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(1)),
                        () -> assertEquals((byte) 120, mongoResultSet.getByte(1)),
                        () -> assertEquals((short) 120, mongoResultSet.getShort(1)),
                        () -> assertEquals(120, mongoResultSet.getInt(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(1)));
            }
        }

        @Test
        void testGettersForLong() throws SQLException {
            var value = new BsonInt64(12345678);
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(1)),
                        () -> assertEquals(12345678L, mongoResultSet.getLong(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(1)));
            }
        }

        @Test
        void testGettersForString() throws SQLException {
            var value = new BsonString("Hello World");
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertEquals("Hello World", mongoResultSet.getString(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(1)));
            }
        }

        @Test
        void testGettersForBigDecimal() throws SQLException {
            var bigDecimalValue = new BigDecimal("10692467440017.111");
            var value = new BsonDecimal128(new Decimal128(bigDecimalValue));
            try (MongoResultSet mongoResultSet = createResultSetWith(value)) {
                assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(1)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(1)),
                        () -> assertEquals(bigDecimalValue, mongoResultSet.getBigDecimal(1)));
            }
        }
    }
}

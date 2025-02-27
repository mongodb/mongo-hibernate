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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

import com.mongodb.client.MongoCursor;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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
import org.bson.BsonType;
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
            return getClosedPreconditionInvocations();
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

    static Stream<Arguments> getClosedPreconditionInvocations() {
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

    @ParameterizedTest(name = "columnIndex: {0}, bsonValue: {1}")
    @MethodSource("getArgumentsStreamForGetValuesTest")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testGetValues(int columnIndex, BsonValue bsonValue, Map<Class<?>, Object> validMapping) throws SQLException {

        var mapEntries = List.of(
                Map.entry("f1", new BsonNull()),
                Map.entry("f2", new BsonBoolean(true)),
                Map.entry("f3", new BsonBoolean(false)),
                Map.entry("f4", new BsonDouble(3.1415)),
                Map.entry("f5", new BsonInt32(120)),
                Map.entry("f6", new BsonInt64(12345678)),
                Map.entry("f7", new BsonString("Hello World")),
                Map.entry("f8", new BsonDecimal128(new Decimal128(new BigDecimal(12345678)))));

        var bsonDocument = new BsonDocument();
        bsonDocument.putAll(
                Map.ofEntries(mapEntries.toArray(new Map.Entry[0]))); // use map to randomize the field order

        doReturn(true).when(mongoCursor).hasNext();
        doReturn(bsonDocument).when(mongoCursor).next();

        var fields = mapEntries.stream().map(Map.Entry::getKey).toList();
        mongoResultSet = new MongoResultSet(mongoCursor, fields);

        assertTrue(mongoResultSet.next());

        // Boolean type
        var expectedBooleanValue = validMapping.get(Boolean.class);
        if (expectedBooleanValue != null) {
            assertEquals(expectedBooleanValue, mongoResultSet.getBoolean(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(columnIndex));
        }

        // Byte type
        var expectedByteValue = validMapping.get(Byte.class);
        if (expectedByteValue != null) {
            assertEquals(expectedByteValue, mongoResultSet.getByte(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getByte(columnIndex));
        }

        // Short type
        var expectedShortValue = validMapping.get(Short.class);
        if (expectedShortValue != null) {
            assertEquals(expectedShortValue, mongoResultSet.getShort(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getShort(columnIndex));
        }

        // Integer type
        var expectedIntegerValue = validMapping.get(Integer.class);
        if (expectedIntegerValue != null) {
            assertEquals(expectedIntegerValue, mongoResultSet.getInt(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getInt(columnIndex));
        }

        // Long type
        var expectedLongValue = validMapping.get(Long.class);
        if (expectedLongValue != null) {
            assertEquals(expectedLongValue, mongoResultSet.getLong(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getLong(columnIndex));
        }

        // Float type
        var expectedFloatValue = validMapping.get(Float.class);
        if (expectedFloatValue != null) {
            assertEquals(expectedFloatValue, mongoResultSet.getFloat(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getFloat(columnIndex));
        }

        // Double type
        var expectedDoubleValue = validMapping.get(Double.class);
        if (expectedDoubleValue != null) {
            assertEquals(expectedDoubleValue, mongoResultSet.getDouble(columnIndex));
        } else {
            assertThrows(SQLException.class, () -> mongoResultSet.getDouble(columnIndex));
        }

        // String type
        var expectedStringValue = validMapping.get(String.class);
        if (expectedStringValue != null) {
            assertEquals(expectedStringValue, mongoResultSet.getString(columnIndex));
        } else {
            if (bsonValue.getBsonType() == BsonType.NULL) {
                assertNull(mongoResultSet.getString(columnIndex));
            } else {
                assertThrows(SQLException.class, () -> mongoResultSet.getString(columnIndex));
            }
        }

        // BigDecimal type
        var expectedBigDecimalValue = validMapping.get(BigDecimal.class);
        if (expectedBigDecimalValue != null) {
            assertEquals(expectedBigDecimalValue, mongoResultSet.getBigDecimal(columnIndex));
        } else {
            if (bsonValue.getBsonType() == BsonType.NULL) {
                assertNull(mongoResultSet.getBigDecimal(columnIndex));
            } else {
                assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(columnIndex));
            }
        }
    }

    private static Stream<Arguments> getArgumentsStreamForGetValuesTest() {
        return Arrays.stream(new Arguments[] {
            Arguments.of(
                    1,
                    new BsonNull(),
                    Map.of(
                            Boolean.class,
                            false,
                            Byte.class,
                            (byte) 0,
                            Short.class,
                            (short) 0,
                            Integer.class,
                            0,
                            Long.class,
                            0L,
                            Float.class,
                            0F,
                            Double.class,
                            0D)),
            Arguments.of(2, new BsonBoolean(true), Map.of(Boolean.class, true)),
            Arguments.of(3, new BsonBoolean(false), Map.of(Boolean.class, false)),
            Arguments.of(4, new BsonDouble(3.1415), Map.of(Float.class, 3.1415F, Double.class, 3.1415)),
            Arguments.of(
                    5,
                    new BsonInt32(120),
                    Map.of(
                            Byte.class, (byte) 120,
                            Short.class, (short) 120,
                            Integer.class, 120)),
            Arguments.of(6, new BsonInt64(12345678L), Map.of(Long.class, 12345678L)),
            Arguments.of(7, new BsonString("Hello World"), Map.of(String.class, "Hello World")),
            Arguments.of(
                    8, new BsonDecimal128(new Decimal128(12345678)), Map.of(BigDecimal.class, new BigDecimal(12345678)))
        });
    }
}

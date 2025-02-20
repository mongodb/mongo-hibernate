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
import java.util.UUID;
import java.util.stream.Stream;
import org.bson.BsonBinary;
import org.bson.BsonBoolean;
import org.bson.BsonDecimal128;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    void setUp() {
        mongoResultSet = new MongoResultSet(mongoCursor, FIELDS);
    }

    @Nested
    class CloseTests {

        @BeforeEach
        void setUp() throws SQLException {
            mongoResultSet.close();
        }

        @Test
        @DisplayName("No-op when 'close()' is called on a closed MongoResultSet")
        void testNoopWhenCloseResultSetClosed() {
            assertDoesNotThrow(() -> mongoResultSet.close());
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoResultSet")
        @MethodSource("getMongoResultSetMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, ResultSetMethodInvocation methodInvocation) {
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertEquals("MongoResultSet has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsImpactedByClosing() {
            return doGetMongoResultSetMethodInvocationsImpactedByClosing();
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
            return doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(0);
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsWithColumnIndexOverflow() {
            return doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(FIELDS.size() + 1);
        }
    }

    static Stream<Arguments> doGetMongoResultSetMethodInvocationsImpactedByClosing() {
        var invocationsImpactedByColumnIndex = doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(1);
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

    static Stream<Arguments> doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(int columnIndex) {
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

    @FunctionalInterface
    interface ResultSetMethodInvocation {
        void runOn(ResultSet rs) throws SQLException;
    }

    @Test
    void testGetValues() throws SQLException {

        // given
        var bytes = UUID.randomUUID().toString().getBytes();
        var string = "Hello World";
        var bigDecimal = new BigDecimal(12345678);
        var entries = List.of(
                Map.entry("f1", new BsonNull()),
                Map.entry("f2", new BsonBoolean(true)),
                Map.entry("f3", new BsonDouble(3.1415)),
                Map.entry("f4", new BsonInt32(120)),
                Map.entry("f5", new BsonInt64(12345678)),
                Map.entry("f6", new BsonBinary(bytes)),
                Map.entry("f7", new BsonString(string)),
                Map.entry("f8", new BsonDecimal128(new Decimal128(bigDecimal))));

        var bsonDocument = new BsonDocument();
        entries.forEach(entry -> bsonDocument.put(entry.getKey(), entry.getValue()));

        doReturn(true).when(mongoCursor).hasNext();
        doReturn(bsonDocument).when(mongoCursor).next();

        // when
        mongoResultSet = new MongoResultSet(
                mongoCursor, entries.stream().map(Map.Entry::getKey).toList());

        // then
        assertTrue(mongoResultSet.next());

        assertAll(
                // f1: new BsonNull()
                () -> assertAll(
                        () -> assertNull(mongoResultSet.getString(1)),
                        () -> assertFalse(mongoResultSet.getBoolean(1)),
                        () -> assertEquals((byte) 0, mongoResultSet.getByte(1)),
                        () -> assertEquals((short) 0, mongoResultSet.getShort(1)),
                        () -> assertEquals(0, mongoResultSet.getInt(1)),
                        () -> assertEquals(0L, mongoResultSet.getLong(1)),
                        () -> assertEquals(0f, mongoResultSet.getFloat(1)),
                        () -> assertEquals(0d, mongoResultSet.getDouble(1)),
                        () -> assertNull(mongoResultSet.getBytes(1)),
                        () -> assertNull(mongoResultSet.getBigDecimal(1))),

                // f2: new new BsonBoolean(true)
                () -> assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(2)),
                        () -> assertTrue(mongoResultSet.getBoolean(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBytes(2)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(2))),

                // f3: new BsonDouble(3.1415)
                () -> assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(3)),
                        () -> assertEquals(3.1415f, mongoResultSet.getFloat(3)),
                        () -> assertEquals(3.1415d, mongoResultSet.getDouble(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBytes(3)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(3))),

                // f4: new BsonInt32(2015)
                () -> assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(4)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(4)),
                        () -> assertEquals((byte) 120, mongoResultSet.getByte(4)),
                        () -> assertEquals((short) 120, mongoResultSet.getShort(4)),
                        () -> assertEquals(120, mongoResultSet.getInt(4)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(4)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(4)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(4)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBytes(4)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(4))),

                // f5: new BsonInt64(12345678)
                () -> assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(5)),
                        () -> assertEquals(12345678L, mongoResultSet.getLong(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBytes(5)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(5))),

                // f6: new BsonBinary(bytes)
                () -> assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(6)),
                        () -> assertEquals(bytes, mongoResultSet.getBytes(6)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(6))),

                // f7: new BsonString(string)
                () -> assertAll(
                        () -> assertEquals(string, mongoResultSet.getString(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBytes(7)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBigDecimal(7))),

                // f8: new new BsonDecimal128(bigDecimal))
                () -> assertAll(
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getString(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBoolean(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getByte(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getShort(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getInt(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getLong(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getFloat(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getDouble(8)),
                        () -> assertThrows(SQLException.class, () -> mongoResultSet.getBytes(8)),
                        () -> assertEquals(bigDecimal, mongoResultSet.getBigDecimal(8))));
    }
}

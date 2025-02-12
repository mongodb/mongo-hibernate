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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.client.MongoCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.BsonDocument;
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
        void testNoopWhenCloseStatementClosed() throws SQLException {
            // when && then
            assertDoesNotThrow(() -> mongoResultSet.close());
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoResultSet")
        @MethodSource("getMongoResultSetMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, ResultSetMethodInvocation methodInvocation) throws SQLException {
            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertEquals("MongoResultSet has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsImpactedByClosing() {
            return Map.<String, ResultSetMethodInvocation>ofEntries(
                            Map.entry("next()", ResultSet::next),
                            Map.entry("wasNull()", ResultSet::wasNull),
                            Map.entry("getString(int)", rs -> rs.getString(1)),
                            Map.entry("getBoolean(int)", rs -> rs.getBoolean(1)),
                            Map.entry("getByte(int)", rs -> rs.getByte(1)),
                            Map.entry("getShort(int)", rs -> rs.getShort(1)),
                            Map.entry("getInt(int)", rs -> rs.getInt(1)),
                            Map.entry("getLong(int)", rs -> rs.getLong(1)),
                            Map.entry("getFloat(int)", rs -> rs.getFloat(1)),
                            Map.entry("getDouble(int)", rs -> rs.getDouble(1)),
                            Map.entry("getBytes(int)", rs -> rs.getBytes(1)),
                            Map.entry("getDate(int)", rs -> rs.getDate(1)),
                            Map.entry("getTime(int)", rs -> rs.getTime(1)),
                            Map.entry("getTime(int,Calendar)", rs -> rs.getTime(1, Calendar.getInstance())),
                            Map.entry("getTimestamp(int)", rs -> rs.getTimestamp(1)),
                            Map.entry("getTimestamp(int,Calendar)", rs -> rs.getTimestamp(1, Calendar.getInstance())),
                            Map.entry("getBigDecimal(int)", rs -> rs.getBigDecimal(1)),
                            Map.entry("getArray(int)", rs -> rs.getArray(1)),
                            Map.entry("getObject(int,Class)", rs -> rs.getObject(1, String.class)),
                            Map.entry("getMetaData()", ResultSet::getMetaData),
                            Map.entry("findColumn(String)", rs -> rs.findColumn("name")),
                            Map.entry("isWrapperFor(Class)", rs -> rs.isWrapperFor(MongoResultSet.class)))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }

    @Nested
    class ColumnIndexCheckingTests {

        @ParameterizedTest(
                name = "SQLException is thrown when \"{0}\" is called on a MongoResultSet with columnIndex too low")
        @MethodSource("getMongoResultSetMethodInvocationsWithColumnIndexUnderflow")
        void testColumnIndexUnderflow(String label, ResultSetMethodInvocation methodInvocation) {
            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertTrue(exception.getMessage().startsWith("Invalid column index"));
        }

        @ParameterizedTest(
                name = "SQLException is thrown when \"{0}\" is called on a MongoResultSet with columnIndex too high")
        @MethodSource("getMongoResultSetMethodInvocationsWithColumnIndexOverflow")
        void testColumnIndexOverflow(String label, ResultSetMethodInvocation methodInvocation) {
            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertTrue(exception.getMessage().startsWith("Invalid column index"));
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsWithColumnIndexUnderflow() {
            return doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(0);
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsWithColumnIndexOverflow() {
            return doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(FIELDS.size() + 1);
        }

        private static Stream<Arguments> doGetMongoResultSetMethodInvocationsImpactedByColumnIndex(int columnIndex) {
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
    }

    @FunctionalInterface
    interface ResultSetMethodInvocation {
        void runOn(ResultSet rs) throws SQLException;
    }
}

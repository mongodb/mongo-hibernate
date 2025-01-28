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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.ServerAddress;
import com.mongodb.ServerCursor;
import com.mongodb.client.MongoCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class MongoResultSetTests {

    @Test
    void test() throws SQLException {
        // given
        var docs = List.of(
                BsonDocument.parse(
                        """
                            {
                                _id: 1,
                                title: "War and Peace",
                                author: "Leo Tolstoy",
                                outOfStock: true
                            }"""),
                BsonDocument.parse(
                        """
                            {
                                _id: 2,
                                title: "Anna Karenina",
                                outOfStock: true
                            }"""),
                BsonDocument.parse(
                        """
                           {
                               _id: 3,
                               title: "Crime and Punishment",
                               author: "Fyodor Dostoevsky",
                               outOfStock: false
                           }"""));

        var fields = List.of("author", "title", "outOfStock");

        var docIter = docs.iterator();
        try (var cursor = new MongoCursor<BsonDocument>() {
                    @Override
                    public void close() {
                        // no-op
                    }

                    @Override
                    public boolean hasNext() {
                        return docIter.hasNext();
                    }

                    @Override
                    public BsonDocument next() {
                        return docIter.next();
                    }

                    @Override
                    public int available() {
                        throw new RuntimeException("unsupported");
                    }

                    @Override
                    public BsonDocument tryNext() {
                        throw new RuntimeException("unsupported");
                    }

                    @Override
                    public ServerCursor getServerCursor() {
                        throw new RuntimeException("unsupported");
                    }

                    @Override
                    public ServerAddress getServerAddress() {
                        throw new RuntimeException("unsupported");
                    }
                };
                var rs = new MongoResultSet(cursor, fields)) {
            assertTrue(rs.next());
            assertAll(
                    () -> assertEquals("Leo Tolstoy", rs.getString(1)),
                    () -> assertEquals("War and Peace", rs.getString(2)),
                    () -> assertTrue(rs.getBoolean(3)));

            assertTrue(rs.next());
            assertAll(
                    () -> assertNull(rs.getString(1)),
                    () -> assertEquals("Anna Karenina", rs.getString(2)),
                    () -> assertTrue(rs.getBoolean(3)));

            assertTrue(rs.next());
            assertAll(
                    () -> assertEquals("Fyodor Dostoevsky", rs.getString(1)),
                    () -> assertEquals("Crime and Punishment", rs.getString(2)),
                    () -> assertFalse(rs.getBoolean(3)));
            assertFalse(rs.next());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CloseTests {
        @Mock
        private MongoCursor<BsonDocument> mongoCursor;

        @InjectMocks
        private MongoResultSet mongoResultSet;

        @Test
        @DisplayName("No-op when 'close()' is called on a closed MongoResultSet")
        void testNoopWhenCloseStatementClosed() throws SQLException {
            // given
            mongoResultSet.close();

            // when && then
            assertDoesNotThrow(() -> mongoResultSet.close());
        }

        @FunctionalInterface
        interface ResultSetMethodInvocation {
            void runOn(ResultSet rs) throws SQLException;
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoResultSet")
        @MethodSource("getMongoResultSetMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, MongoResultSetTests.CloseTests.ResultSetMethodInvocation methodInvocation)
                throws SQLException {
            // given
            mongoResultSet.close();

            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoResultSet));
            assertEquals("MongoResultSet has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoResultSetMethodInvocationsImpactedByClosing() {
            return Map.<String, MongoResultSetTests.CloseTests.ResultSetMethodInvocation>ofEntries(
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
}

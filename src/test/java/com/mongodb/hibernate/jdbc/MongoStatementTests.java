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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Statement;
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

@ExtendWith(MockitoExtension.class)
class MongoStatementTests {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private ClientSession clientSession;

    @Mock
    private MongoConnection mongoConnection;

    @InjectMocks
    private MongoStatement mongoStatement;

    @Test
    @DisplayName("No-op when 'close()' is called on a closed MongoStatement")
    void testNoopWhenCloseStatementClosed() {
        // given
        mongoStatement.close();
        assertTrue(mongoStatement.isClosed());

        // when && then
        assertDoesNotThrow(() -> mongoStatement.close());
    }

    @Nested
    class ExecuteUpdateTests {

        @Test
        @DisplayName("SQLException is thrown when 'mql' is invalid")
        void testSQLExceptionThrownWhenCalledWithInvalidMql() {
            // given
            String invalidMql =
                    """
                { insert: "books"'", documents: [ { title: "War and Peace" } ]
                """;

            // when && then
            assertThrows(SQLSyntaxErrorException.class, () -> mongoStatement.executeUpdate(invalidMql));
        }

        @Test
        @DisplayName("SQLException is thrown when database access error occurs")
        void testSQLExceptionThrownWhenDBAccessFailed(@Mock MongoDatabase mongoDatabase) {
            // given
            doReturn(mongoDatabase).when(mongoClient).getDatabase(anyString());
            var dbAccessException = new RuntimeException();
            doThrow(dbAccessException).when(mongoDatabase).runCommand(same(clientSession), any(BsonDocument.class));
            String mql =
                    """
                {
                    delete: "orders",
                    deletes: [ { q: { status: "D" }, limit: 1 }, { q: { outOfStock: true }, limit: 0 } ]
                }
                """;

            // when && then
            var sqlException = assertThrows(SQLException.class, () -> mongoStatement.executeUpdate(mql));
            assertEquals(dbAccessException, sqlException.getCause());
        }
    }

    @Nested
    class ClosedTests {

        @FunctionalInterface
        interface StatementMethodInvocation {
            void runOn(MongoStatement stmt) throws SQLException;
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoStatement")
        @MethodSource("getMongoStatementMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, StatementMethodInvocation methodInvocation) {
            // given
            mongoStatement.close();

            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoStatement));
            assertEquals("MongoStatement has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoStatementMethodInvocationsImpactedByClosing() {
            var exampleQueryMql =
                    """
                {
                  find: "restaurants",
                  filter: { rating: { $gte: 9 }, cuisine: "italian" },
                  projection: { name: 1, rating: 1, address: 1 },
                  sort: { name: 1 },
                  limit: 5
                }""";
            var exampleUpdateMql =
                    """
                {
                  update: "members",
                  updates: [
                    {
                      q: {},
                      u: { $inc: { points: 1 } },
                      multi: true
                    }
                  ]
                }""";
            return Map.<String, StatementMethodInvocation>ofEntries(
                            Map.entry("executeQuery(String)", stmt -> stmt.executeQuery(exampleQueryMql)),
                            Map.entry("executeUpdate(String)", stmt -> stmt.executeUpdate(exampleUpdateMql)),
                            Map.entry("getMaxRows()", MongoStatement::getMaxRows),
                            Map.entry("setMaxRows(int)", stmt -> stmt.setMaxRows(10)),
                            Map.entry("getQueryTimeout()", MongoStatement::getQueryTimeout),
                            Map.entry("setQueryTimeout(int)", stmt -> stmt.setQueryTimeout(1)),
                            Map.entry("cancel()", MongoStatement::cancel),
                            Map.entry("getWarnings()", MongoStatement::getWarnings),
                            Map.entry("clearWarnings()", MongoStatement::clearWarnings),
                            Map.entry("execute(String)", stmt -> stmt.execute(exampleQueryMql)),
                            Map.entry("getResultSet()", MongoStatement::getResultSet),
                            Map.entry("getMoreResultSet()", MongoStatement::getMoreResults),
                            Map.entry("getUpdateCount()", MongoStatement::getUpdateCount),
                            Map.entry("setFetchSize(int)", stmt -> stmt.setFetchSize(1)),
                            Map.entry("getFetchSize()", MongoStatement::getFetchSize),
                            Map.entry("addBatch(String)", stmt -> stmt.addBatch(exampleUpdateMql)),
                            Map.entry("clearBatch()", MongoStatement::clearBatch),
                            Map.entry("executeBatch()", MongoStatement::executeBatch),
                            Map.entry("getConnection()", MongoStatement::getConnection),
                            Map.entry("isWrapperFor(Class)", stmt -> stmt.isWrapperFor(Statement.class)))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }
}

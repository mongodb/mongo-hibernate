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
import static org.mockito.Answers.RETURNS_SMART_NULLS;
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
import java.util.HashMap;
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

    @Mock(answer = RETURNS_SMART_NULLS)
    private MongoClient mongoClient;

    @Mock(answer = RETURNS_SMART_NULLS)
    private ClientSession clientSession;

    @Mock(answer = RETURNS_SMART_NULLS)
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
                    {insert: "books"'", documents: [ { title: "War and Peace" } ]
                    """;

            // when && then
            assertThrows(SQLSyntaxErrorException.class, () -> mongoStatement.executeUpdate(invalidMql));
        }

        @Test
        @DisplayName("SQLException is thrown when database access error occurs")
        void testSQLExceptionThrownWhenDBAccessFailed(@Mock(answer = RETURNS_SMART_NULLS) MongoDatabase mongoDatabase) {
            // given
            doReturn(mongoDatabase).when(mongoClient).getDatabase(anyString());
            doThrow(new RuntimeException())
                    .when(mongoDatabase)
                    .runCommand(same(clientSession), any(BsonDocument.class));
            String mql =
                    """
                    {
                          delete: "orders",
                          deletes: [ { q: { status: "D" }, limit: 1 }, { q: { outOfStock: true }, limit: 0 } ]
                       }
                    """;

            // when && then
            assertThrows(SQLException.class, () -> mongoStatement.executeUpdate(mql));
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
            var mql =
                    """
                     {
                        insert: "books",
                        documents: [
                            {
                                title: "War and Peace",
                                author: "Leo Tolstoy",
                                outOfStock: false,
                                values: [
                                    { $undefined: true }
                                ]
                            }
                        ]
                     }""";

            mongoStatement.close();

            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoStatement));
            assertEquals("Statement has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoStatementMethodInvocationsImpactedByClosing() {
            var map = new HashMap<String, StatementMethodInvocation>();
            map.put("executeQuery(String)", stmt -> stmt.executeQuery(null));
            map.put("executeUpdate(String)", stmt -> stmt.executeUpdate(null));
            map.put("getMaxRows()", MongoStatement::getMaxRows);
            map.put("setMaxRows(int)", pstmt -> pstmt.setMaxRows(10));
            map.put("getQueryTimeout()", MongoStatement::getQueryTimeout);
            map.put("setQueryTimeout(int)", pstmt -> pstmt.setQueryTimeout(1));
            map.put("cancel()", MongoStatement::cancel);
            map.put("getWarnings()", MongoStatement::getWarnings);
            map.put("clearWarnings()", MongoStatement::clearWarnings);
            map.put("execute(String)", pstmt -> pstmt.execute(null));
            map.put("getResultSet()", MongoStatement::getResultSet);
            map.put("getMoreResultSet()", MongoStatement::getMoreResults);
            map.put("getUpdateCount()", MongoStatement::getUpdateCount);
            map.put("setFetchSize(int)", pstmt -> pstmt.setFetchSize(1));
            map.put("getFetchSize()", MongoStatement::getFetchSize);
            map.put("addBatch(String)", stmt -> stmt.addBatch(null));
            map.put("clearBatch()", MongoStatement::clearBatch);
            map.put("executeBatch()", MongoStatement::executeBatch);
            map.put("getConnection()", MongoStatement::getConnection);
            map.put("getGeneratedKeys()", MongoStatement::getGeneratedKeys);
            map.put("unwrap(Class)", pstmt -> pstmt.unwrap(null));
            map.put("isWrapperFor(Class)", pstmt -> pstmt.isWrapperFor(null));
            return map.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }
}

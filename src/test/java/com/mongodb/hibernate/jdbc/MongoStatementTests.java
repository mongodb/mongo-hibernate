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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import org.bson.BsonDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoStatementTests {

    @Mock(answer = RETURNS_SMART_NULLS)
    private MongoDatabase mongoDatabase;

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

    @Test
    @DisplayName("SQLException is thrown when 'getConnection()' is called on a closed MongoStatement")
    void testSQLExceptionThrownWhenGetConnectionOnClosedStatement() {
        // given
        mongoStatement.close();

        // when && then
        assertThrows(SQLException.class, () -> mongoStatement.getConnection());
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
        @DisplayName("SQLException is thrown in event of unsupported command type")
        void testSQLExceptionThrownWhenUpdateCommandTypeUnsupported() {
            // given
            String mql =
                    """
                    {
                       aggregate: "articles",
                       pipeline: [
                          { $project: { tags: 1 } },
                          { $unwind: "$tags" },
                          { $group: { _id: "$tags", count: { $sum : 1 } } }
                       ]
                    }
                    """;

            // when && then
            assertThrows(SQLFeatureNotSupportedException.class, () -> mongoStatement.executeUpdate(mql));
        }

        @Test
        @DisplayName("SQLException is thrown when MongoStatement has been closed")
        void testSQLExceptionThrownWhenStatementClosed() {
            // given
            mongoStatement.close();

            String mql =
                    """
                    {
                          delete: "orders",
                          deletes: [ { q: { status: "D" }, limit: 1 } ]
                       }
                    """;

            // when && then
            assertThrows(SQLException.class, () -> mongoStatement.executeUpdate(mql));
        }

        @Test
        @DisplayName("SQLException is thrown when database access error occurs")
        void testSQLExceptionThrownWhenDBAccessFailed() {
            // given
            doThrow(new RuntimeException())
                    .when(mongoDatabase)
                    .runCommand(same(clientSession), any(BsonDocument.class));
            String mql =
                    """
                    {
                          delete: "orders",
                          deletes: [ { q: { status: "D" }, limit: 1 } ]
                       }
                    """;

            // when && then
            assertThrows(SQLException.class, () -> mongoStatement.executeUpdate(mql));
        }
    }
}

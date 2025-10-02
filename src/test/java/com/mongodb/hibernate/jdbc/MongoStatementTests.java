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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import com.mongodb.bulk.BulkWriteInsert;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.util.List;
import java.util.function.BiConsumer;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoStatementTests {

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private MongoCollection<BsonDocument> mongoCollection;

    @Mock
    private ClientSession clientSession;

    @Mock
    private MongoConnection mongoConnection;

    private MongoStatement mongoStatement;

    @BeforeEach
    void beforeEach() {
        mongoStatement = new MongoStatement(mongoDatabase, clientSession, mongoConnection);
    }

    @Test
    void testNoopWhenCloseStatementClosed() throws SQLException {

        mongoStatement.close();
        assertTrue(mongoStatement.isClosed());

        assertDoesNotThrow(() -> mongoStatement.close());
    }

    @Test
    void testResultSetClosedWhenStatementClosed(
            @Mock MongoCollection<BsonDocument> mongoCollection,
            @Mock AggregateIterable<BsonDocument> aggregateIterable,
            @Mock MongoCursor<BsonDocument> mongoCursor)
            throws SQLException {

        doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
        doReturn(aggregateIterable).when(mongoCollection).aggregate(same(clientSession), anyList());
        doReturn(mongoCursor).when(aggregateIterable).cursor();

        var query =
                """
                {
                    aggregate: "books",
                    pipeline: [
                        { $match: { _id: { $eq: 1 } } },
                        { $project: { _id: 0, title: 1, publishYear: 1 } }
                    ]
                }""";

        var resultSet = mongoStatement.executeQuery(query);
        mongoStatement.close();

        assertTrue(resultSet.isClosed());
    }

    @Nested
    class ExecuteMethodClosesLastOpenResultSetTests {

        private final String exampleQueryMql =
                """
                {
                    aggregate: "books",
                    pipeline: [
                        { $match: { _id: { $eq: 1 } } },
                        { $project: { _id: 0, title: 1, publishYear: 1 } }
                    ]
                }""";
        private final String exampleUpdateMql =
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

        @Mock
        AggregateIterable<BsonDocument> aggregateIterable;

        @Mock
        MongoCursor<BsonDocument> mongoCursor;

        private static final BulkWriteResult BULK_WRITE_RESULT = BulkWriteResult.acknowledged(
                1, 0, 2, 3, emptyList(),
                List.of(new BulkWriteInsert(0, new BsonObjectId(new ObjectId(1, 1)))));

        private ResultSet lastOpenResultSet;

        @BeforeEach
        void beforeEach() throws SQLException {
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(aggregateIterable).when(mongoCollection).aggregate(same(clientSession), anyList());
            doReturn(mongoCursor).when(aggregateIterable).cursor();

            lastOpenResultSet = mongoStatement.executeQuery(exampleQueryMql);
            assertFalse(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteQuery() throws SQLException {
            mongoStatement.executeQuery(exampleQueryMql);
            assertTrue(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteUpdate() throws SQLException {
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(BULK_WRITE_RESULT).when(mongoCollection).bulkWrite(eq(clientSession), anyList());

            mongoStatement.executeUpdate(exampleUpdateMql);
            assertTrue(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecute() throws SQLException {
            assertThrows(SQLFeatureNotSupportedException.class, () -> mongoStatement.execute(exampleUpdateMql));
            assertTrue(lastOpenResultSet.isClosed());
        }
    }

    @Test
    void testGetProjectStageFieldNames() {
        BiConsumer<String, List<String>> successAsserter = (projectStage, expectedFieldNames) -> assertEquals(
                expectedFieldNames, MongoStatement.getFieldNamesFromProjectStage(BsonDocument.parse(projectStage)));
        BiConsumer<String, String> failureAsserter = (projectStage, expectedMessageFragment) -> {
            Throwable e = assertThrows(
                    RuntimeException.class,
                    () -> MongoStatement.getFieldNamesFromProjectStage(BsonDocument.parse(projectStage)));
            assertThat(e.getMessage()).contains(expectedMessageFragment);
        };
        assertAll(
                () -> successAsserter.accept("{_id: 1, title: 1}", List.of("_id", "title")),
                () -> successAsserter.accept("{_id: 1, title: -1}", List.of("_id", "title")),
                () -> successAsserter.accept("{_id: 1, title: 2}", List.of("_id", "title")),
                () -> successAsserter.accept("{title: 1, _id: 0}", List.of("title")),
                () -> successAsserter.accept("{title: 1, _id: false}", List.of("title")),
                () -> successAsserter.accept("{title: 1, publishYear: 1}", List.of("title", "publishYear", "_id")),
                () -> failureAsserter.accept("{title: 1, publishYear: 0}", "Exclusions are not allowed"),
                () -> failureAsserter.accept("{title: 1, publishYear: false}", "Exclusions are not allowed"),
                () -> failureAsserter.accept("{title: '$field.path'}", "Expressions and literals are not supported"),
                () -> failureAsserter.accept("{title: '$$REMOVE'}", "Expressions and literals are not supported"),
                () -> failureAsserter.accept("{title: {$literal: 1}}", "Expressions and literals are not supported"),
                () -> failureAsserter.accept("{title: 'string literal'}", "Expressions and literals are not supported"),
                () -> failureAsserter.accept("{title: []}", "Expressions and literals are not supported"),
                () -> failureAsserter.accept(
                        "{title: ['array literal']}", "Expressions and literals are not supported"),
                () -> failureAsserter.accept(
                        "{title: {fieldName: 'document literal'}}", "Expressions and literals are not supported"));
    }

    @Nested
    class ExecuteUpdateTests {

        @Test
        void testSQLExceptionThrownWhenCalledWithInvalidMql() {

            String invalidMql =
                    """
                    { insert: "books"'", documents: [ { title: "War and Peace" } ]
                    """;

            assertThrows(SQLSyntaxErrorException.class, () -> mongoStatement.executeUpdate(invalidMql));
        }

        @Test
        void testSQLExceptionThrownWhenDBAccessFailed() {
            var dbAccessException = new RuntimeException();
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doThrow(dbAccessException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            String mql =
                    """
                    {
                        delete: "orders",
                        deletes: [ { q: { status: "D" }, limit: 1 }, { q: { outOfStock: true }, limit: 0 } ]
                    }
                    """;

            var sqlException = assertThrows(SQLException.class, () -> mongoStatement.executeUpdate(mql));
            assertEquals(dbAccessException, sqlException.getCause());
        }
    }

    @Test
    void testCheckClosed() throws SQLException {
        mongoStatement.close();
        checkMethodsWithOpenPrecondition();
    }

    private void checkMethodsWithOpenPrecondition() {
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
        assertAll(
                () -> assertThrowsClosedException(() -> mongoStatement.executeQuery(exampleQueryMql)),
                () -> assertThrowsClosedException(() -> mongoStatement.executeUpdate(exampleUpdateMql)),
                () -> assertThrowsClosedException(mongoStatement::cancel),
                () -> assertThrowsClosedException(mongoStatement::getWarnings),
                () -> assertThrowsClosedException(mongoStatement::clearWarnings),
                () -> assertThrowsClosedException(() -> mongoStatement.execute(exampleUpdateMql)),
                () -> assertThrowsClosedException(mongoStatement::getResultSet),
                () -> assertThrowsClosedException(mongoStatement::getMoreResults),
                () -> assertThrowsClosedException(mongoStatement::getUpdateCount),
                () -> assertThrowsClosedException(mongoStatement::getConnection),
                () -> assertThrowsClosedException(() -> mongoStatement.isWrapperFor(MongoStatement.class)));
    }

    private static void assertThrowsClosedException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertThat(exception.getMessage()).isEqualTo("MongoStatement has been closed");
    }
}

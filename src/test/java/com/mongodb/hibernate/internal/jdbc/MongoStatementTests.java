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

package com.mongodb.hibernate.internal.jdbc;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.util.List;
import java.util.function.BiConsumer;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
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
            @Mock AggregateIterable<BsonDocument> aggregateIterable, @Mock MongoCursor<BsonDocument> mongoCursor)
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

        private static final String EXAMPLE_QUERY_MQL =
                """
                {
                    aggregate: "books",
                    pipeline: [
                        { $match: { _id: { $eq: 1 } } },
                        { $project: { _id: 0, title: 1, publishYear: 1 } }
                    ]
                }""";
        private static final String EXAMPLE_UPDATE_MQL =
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

        private ResultSet lastOpenResultSet;

        @BeforeEach
        void beforeEach() throws SQLException {
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(aggregateIterable).when(mongoCollection).aggregate(same(clientSession), anyList());
            doReturn(mongoCursor).when(aggregateIterable).cursor();

            lastOpenResultSet = mongoStatement.executeQuery(EXAMPLE_QUERY_MQL);
            assertFalse(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteQuery() throws SQLException {
            mongoStatement.executeQuery(EXAMPLE_QUERY_MQL);
            assertTrue(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteUpdate() throws SQLException {
            doReturn(BulkWriteResult.acknowledged(0, 0, 0, 0, emptyList(), emptyList()))
                    .when(mongoCollection)
                    .bulkWrite(eq(clientSession), anyList());

            mongoStatement.executeUpdate(EXAMPLE_UPDATE_MQL);
            assertTrue(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteUpdateReturnsMatchedCount() throws SQLException {
            doReturn(BulkWriteResult.acknowledged(0, 2, 0, 1, emptyList(), emptyList()))
                    .when(mongoCollection)
                    .bulkWrite(eq(clientSession), anyList());

            assertEquals(2, mongoStatement.executeUpdate(EXAMPLE_UPDATE_MQL));
        }

        @Test
        void testExecute() throws SQLException {
            doReturn(BulkWriteResult.acknowledged(0, 1, 0, 0, emptyList(), emptyList()))
                    .when(mongoCollection)
                    .bulkWrite(eq(clientSession), anyList());

            mongoStatement.execute(EXAMPLE_UPDATE_MQL);
            assertTrue(lastOpenResultSet.isClosed());
            verify(mongoCollection).bulkWrite(eq(clientSession), anyList());
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
                () -> successAsserter.accept("{title: 1, publishYear: 1}", List.of("title", "publishYear")),
                () -> failureAsserter.accept("{title: 1, publishYear: 0}", "Exclusions are not allowed"),
                () -> failureAsserter.accept("{title: 1, publishYear: false}", "Exclusions are not allowed"),
                () -> successAsserter.accept("{'o1_0#total': '$o1_0.total', _id: 1}", List.of("o1_0#total", "_id")),
                () -> failureAsserter.accept("{title: '$$REMOVE'}", "Unsupported value in '$project' specification"),
                () -> successAsserter.accept("{title: {$literal: 1}}", List.of("title")),
                () -> failureAsserter.accept(
                        "{title: 'string literal'}", "Unsupported value in '$project' specification"),
                () -> failureAsserter.accept("{title: []}", "Unsupported value in '$project' specification"),
                () -> failureAsserter.accept(
                        "{title: ['array literal']}", "Unsupported value in '$project' specification"),
                () -> successAsserter.accept("{title: {fieldName: 'document literal'}}", List.of("title")));
    }

    @Test
    void findAndModifyIsRejectedByExecuteUpdate() {
        var command = BsonDocument.parse(
                """
                {
                  "findAndModify": "hibernate_sequences",
                  "query": {"_id": "books_SEQ"}
                }""");
        assertThatThrownBy(() -> MongoStatement.checkSupportedUpdateCommand(command))
                .isInstanceOf(SQLFeatureNotSupportedException.class)
                .hasMessage("Unsupported command for executeUpdate: findAndModify");
    }

    /**
     * {@code findAndModify} exists in the adapter to allocate sequence values, which must not join the caller's
     * transaction, so a command that does not opt out is refused rather than run transactionally. The refusal precedes
     * any collection access, which is why this needs no stubbing.
     */
    @Test
    void findAndModifyWithoutOptingOutOfTheTransactionIsRejected() {
        var command = BsonDocument.parse(
                """
                {
                  "findAndModify": "hibernate_sequences",
                  "query": {"_id": "books_SEQ"},
                  "update": [{"$set": {"next_value": {"$add": ["$next_value", 1]}}}],
                  "new": false,
                  "fields": {"_id": 0, "next_value": 1}
                }""");
        assertThatThrownBy(() -> mongoStatement.executeQuery(command))
                .isInstanceOf(SQLFeatureNotSupportedException.class)
                .hasMessageContaining("nonTransactional");
    }

    @Test
    void findAndModifyIsAcceptedByExecuteQuery() {
        var command = BsonDocument.parse(
                """
                {
                  "findAndModify": "hibernate_sequences",
                  "query": {"_id": "books_SEQ"}
                }""");
        assertThatCode(() -> MongoStatement.checkSupportedQueryCommand(command)).doesNotThrowAnyException();
    }

    @Test
    void executeRoutesWriteCommandsAwayFromAdminCommands() throws SQLException {
        var seed =
                """
                {
                  "update": "hibernate_sequences",
                  "updates": [
                    {
                      "q": {"_id": "books_SEQ"},
                      "u": {"$setOnInsert": {"next_value": {"$numberLong": "1"}}},
                      "upsert": true
                    }
                  ]
                }""";
        doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
        doReturn(BulkWriteResult.acknowledged(0, 0, 0, 0, emptyList(), emptyList()))
                .when(mongoCollection)
                .bulkWrite(eq(clientSession), anyList());

        mongoStatement.execute(seed);

        verify(mongoCollection).bulkWrite(eq(clientSession), anyList());
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

    @Nested
    class ExecuteUpdateWithUpsertTests {

        private static final String UPSERT_MQL =
                """
                {
                  update: "items",
                  updates: [
                    {
                      q: { _id: { $eq: 1 } },
                      u: { $set: { v: 10 } },
                      upsert: true,
                      multi: false
                    }
                  ]
                }""";

        @BeforeEach
        void beforeEach() {
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
        }

        @Test
        void testUpsertOptionIsPassedToTheWriteModel() throws SQLException {
            doReturn(BulkWriteResult.acknowledged(0, 1, 0, 0, emptyList(), emptyList()))
                    .when(mongoCollection)
                    .bulkWrite(eq(clientSession), anyList());

            mongoStatement.executeUpdate(UPSERT_MQL);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<WriteModel<BsonDocument>>> modelsCaptor = ArgumentCaptor.forClass(List.class);
            verify(mongoCollection).bulkWrite(eq(clientSession), modelsCaptor.capture());
            var model = (UpdateOneModel<BsonDocument>) modelsCaptor.getValue().get(0);
            assertTrue(model.getOptions().isUpsert());
        }

        @Test
        void testUpdateCountIsMatchedPlusUpserted() throws SQLException {
            doReturn(BulkWriteResult.acknowledged(
                            0, 2, 0, 0, List.of(new BulkWriteUpsert(0, new BsonInt32(1))), emptyList()))
                    .when(mongoCollection)
                    .bulkWrite(eq(clientSession), anyList());

            assertEquals(3, mongoStatement.executeUpdate(UPSERT_MQL));
        }

        @Test
        void testPipelineUpdateUpsertsAsWriteModel() throws SQLException {
            doReturn(BulkWriteResult.acknowledged(0, 1, 0, 0, emptyList(), emptyList()))
                    .when(mongoCollection)
                    .bulkWrite(eq(clientSession), anyList());

            var pipelineUpsertMql =
                    """
                    {
                      update: "items",
                      updates: [
                        {
                          q: { _id: { $eq: 1 } },
                          u: [ { $set: { v: 10 } } ],
                          upsert: true,
                          multi: false
                        }
                      ]
                    }""";

            mongoStatement.executeUpdate(pipelineUpsertMql);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<WriteModel<BsonDocument>>> modelsCaptor = ArgumentCaptor.forClass(List.class);
            verify(mongoCollection).bulkWrite(eq(clientSession), modelsCaptor.capture());
            var model = (UpdateOneModel<BsonDocument>) modelsCaptor.getValue().get(0);
            assertTrue(model.getOptions().isUpsert());
            assertNotNull(model.getUpdatePipeline());
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

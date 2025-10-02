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

import static java.sql.Statement.SUCCESS_NO_INFO;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoOperationTimeoutException;
import com.mongodb.MongoSocketReadTimeoutException;
import com.mongodb.MongoSocketWriteTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteInsert;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.hibernate.internal.type.ObjectIdJdbcType;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.BatchUpdateException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoPreparedStatementTests {

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    MongoCollection<BsonDocument> mongoCollection;

    @Mock
    private ClientSession clientSession;

    @Mock
    private MongoConnection mongoConnection;

    private MongoPreparedStatement createMongoPreparedStatement(String mql) throws SQLSyntaxErrorException {
        return new MongoPreparedStatement(mongoDatabase, clientSession, mongoConnection, mql);
    }

    private static final String EXAMPLE_MQL =
            """
            {
               insert: "items",
               documents: [
                   {
                       string1: { $undefined: true },
                       string2: { $undefined: true },
                       int32: { $undefined: true },
                       boolean: { $undefined: true },
                       stringAndObjectId: [
                           { $undefined: true },
                           { $undefined: true }
                       ]
                       objectId: { $undefined: true }
                   }
               ]
            }
            """;

    @Nested
    class ParameterValueSettingTests {

        @Captor
        private ArgumentCaptor<List<WriteModel<BsonDocument>>> commandCaptor;

        @Test
        @DisplayName("Happy path when all parameters are provided values")
        void testSuccess() throws SQLException {
            BulkWriteResult bulkWriteResult = Mockito.mock(BulkWriteResult.class);

            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(bulkWriteResult).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            doReturn(1).when(bulkWriteResult).getInsertedCount();

            try (var preparedStatement = createMongoPreparedStatement(EXAMPLE_MQL)) {

                preparedStatement.setString(1, "s1");
                preparedStatement.setString(2, "s2");
                preparedStatement.setInt(3, 1);
                preparedStatement.setBoolean(4, true);
                preparedStatement.setString(5, "array element");
                preparedStatement.setObject(6, new ObjectId(1, 2), ObjectIdJdbcType.MQL_TYPE.getVendorTypeNumber());
                preparedStatement.setObject(7, new ObjectId(2, 0), ObjectIdJdbcType.MQL_TYPE.getVendorTypeNumber());

                preparedStatement.executeUpdate();

                verify(mongoCollection).bulkWrite(eq(clientSession), commandCaptor.capture());
                var writeModels = commandCaptor.getValue();
                assertEquals(1, writeModels.size());
                var expectedDoc = new BsonDocument()
                        .append("string1", new BsonString("s1"))
                        .append("string2", new BsonString("s2"))
                        .append("int32", new BsonInt32(1))
                        .append("boolean", BsonBoolean.TRUE)
                        .append(
                                "stringAndObjectId",
                                new BsonArray(
                                        List.of(new BsonString("array element"), new BsonObjectId(new ObjectId(1, 2)))))
                        .append("objectId", new BsonObjectId(new ObjectId(2, 0)));
                assertInstanceOf(InsertOneModel.class, writeModels.get(0));
                assertEquals(expectedDoc, ((InsertOneModel<BsonDocument>) writeModels.get(0)).getDocument());
            }
        }
    }

    @Nested
    class ExecuteMethodThrowsSqlExceptionTests {
        private static final String DUMMY_EXCEPTION_MESSAGE = "Test message";
        private static final ServerAddress DUMMY_SERVER_ADDRESS = new ServerAddress("localhost");

        private static final BulkWriteError BULK_WRITE_ERROR =
                new BulkWriteError(10, DUMMY_EXCEPTION_MESSAGE, new BsonDocument(), 0);
        private static final BulkWriteResult BULK_WRITE_RESULT = BulkWriteResult.acknowledged(
                1, 0, 2, 3, emptyList(), List.of(new BulkWriteInsert(0, new BsonObjectId(new ObjectId(1, 2)))));
        private static final MongoBulkWriteException MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS =
                new MongoBulkWriteException(
                        BULK_WRITE_RESULT, List.of(BULK_WRITE_ERROR), null, DUMMY_SERVER_ADDRESS, emptySet());
        private static final MongoBulkWriteException MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS =
                new MongoBulkWriteException(BULK_WRITE_RESULT, emptyList(), null, DUMMY_SERVER_ADDRESS, emptySet());

        private static final String MQL_ITEMS_AGGREGATE =
                """
                {
                    aggregate: "items",
                    pipeline: [
                        { $match: { _id: 1 } },
                        { $project: { _id: 0 } }
                    ]
                }
                """;

        private static final String MQL_ITEMS_INSERT =
                """
                {
                    insert: "items",
                    documents: [
                        { _id: 1 }
                    ]
                }
                """;
        private static final String MQL_ITEMS_UPDATE =
                """
                {
                    update: "items",
                    updates: [
                        { q: { _id: 1 }, u: { $set: { touched: true } }, multi: false }
                    ]
                }
                """;
        private static final String MQL_ITEMS_DELETE =
                """
                {
                    delete: "items",
                    deletes: [
                        { q: { _id: 1 }, limit: 1 }
                    ]
                }
                """;

        @BeforeEach
        void beforeEach() {
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
        }

        private static Stream<Arguments> exceptions() {
            var dummyCause = new RuntimeException();
            return Stream.of(
                    Arguments.of(new MongoException(DUMMY_EXCEPTION_MESSAGE), SQLException.class),
                    Arguments.of(new RuntimeException(DUMMY_EXCEPTION_MESSAGE), SQLException.class),
                    Arguments.of(
                            new MongoExecutionTimeoutException(DUMMY_EXCEPTION_MESSAGE), SQLTimeoutException.class),
                    Arguments.of(
                            new MongoSocketReadTimeoutException(
                                    DUMMY_EXCEPTION_MESSAGE, DUMMY_SERVER_ADDRESS, dummyCause),
                            SQLTimeoutException.class),
                    Arguments.of(
                            new MongoSocketWriteTimeoutException(
                                    DUMMY_EXCEPTION_MESSAGE, DUMMY_SERVER_ADDRESS, dummyCause),
                            SQLTimeoutException.class),
                    Arguments.of(new MongoTimeoutException(DUMMY_EXCEPTION_MESSAGE), SQLTimeoutException.class),
                    Arguments.of(
                            new MongoOperationTimeoutException(DUMMY_EXCEPTION_MESSAGE), SQLTimeoutException.class));
        }

        @ParameterizedTest(name = "test executeQuery throws SQLException. Parameters: exception={0}")
        @MethodSource("exceptions")
        void testExecuteQueryThrowsSqlException(Exception thrownException, Class<? extends SQLException> expectedType)
                throws SQLException {
            doThrow(thrownException).when(mongoCollection).aggregate(eq(clientSession), anyList());
            assertExecuteThrowsSqlException(
                    MQL_ITEMS_AGGREGATE, MongoPreparedStatement::executeQuery, thrownException, expectedType);
        }

        @ParameterizedTest(name = "test executeUpdate throws SQLException. Parameters: exception={0}")
        @MethodSource("exceptions")
        void testExecuteUpdateThrowsSqlException(Exception thrownException, Class<? extends SQLException> expectedType)
                throws SQLException {
            doThrow(thrownException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            assertExecuteThrowsSqlException(
                    MQL_ITEMS_INSERT, MongoPreparedStatement::executeUpdate, thrownException, expectedType);
        }

        @ParameterizedTest(name = "test executeUpdate throws SQLException. Parameters: exception={0}")
        @MethodSource("exceptions")
        void testExecuteBatchThrowsSqlException(Exception thrownException, Class<? extends SQLException> expectedType)
                throws SQLException {
            doThrow(thrownException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            assertExecuteThrowsSqlException(
                    MQL_ITEMS_INSERT,
                    mongoPreparedStatement -> {
                        mongoPreparedStatement.addBatch();
                        mongoPreparedStatement.executeBatch();
                    },
                    thrownException,
                    expectedType);
        }

        private static Stream<Arguments> bulkWriteExceptionsForExecuteUpdate() {
            return Stream.of(
                    Arguments.of(named("insert", MQL_ITEMS_INSERT), MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS),
                    Arguments.of(named("update", MQL_ITEMS_UPDATE), MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS),
                    Arguments.of(named("delete", MQL_ITEMS_DELETE), MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS),
                    Arguments.of(named("insert", MQL_ITEMS_INSERT), MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS),
                    Arguments.of(named("update", MQL_ITEMS_UPDATE), MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS),
                    Arguments.of(named("delete", MQL_ITEMS_DELETE), MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS));
        }

        @ParameterizedTest(
                name = "test executeUpdate throws SQLException when MongoBulkWriteException occurs."
                        + " Parameters: commandName={0}, exception={1}")
        @MethodSource("bulkWriteExceptionsForExecuteUpdate")
        void testExecuteUpdateThrowsSqlExceptionWhenMongoBulkWriteExceptionOccurs(
                String mql, MongoBulkWriteException mongoBulkWriteException) throws SQLException {
            doThrow(mongoBulkWriteException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            try (MongoPreparedStatement mongoPreparedStatement = createMongoPreparedStatement(mql)) {
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(mongoPreparedStatement::executeUpdate)
                        .withCause(mongoBulkWriteException)
                        .satisfies(sqlException -> {
                            Integer vendorCodeError = getVendorCodeError(mongoBulkWriteException);
                            assertAll(
                                    () -> assertNull(sqlException.getSQLState()),
                                    () -> assertEquals(vendorCodeError, sqlException.getErrorCode()));
                        });
            }
        }

        private static Stream<Arguments> bulkWriteExceptionsForExecuteBatch() {
            return Stream.of(
                    Arguments.of(
                            named("insert", MQL_ITEMS_INSERT),
                            MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS,
                            BULK_WRITE_RESULT.getInsertedCount()),
                    Arguments.of(
                            named("update", MQL_ITEMS_UPDATE),
                            MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS,
                            BULK_WRITE_RESULT.getModifiedCount()),
                    Arguments.of(
                            named("delete", MQL_ITEMS_DELETE),
                            MONGO_BULK_WRITE_EXCEPTION_NO_ERRORS,
                            BULK_WRITE_RESULT.getDeletedCount()),
                    Arguments.of(
                            named("insert", MQL_ITEMS_INSERT),
                            MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS,
                            BULK_WRITE_RESULT.getInsertedCount()),
                    Arguments.of(
                            named("update", MQL_ITEMS_UPDATE),
                            MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS,
                            BULK_WRITE_RESULT.getModifiedCount()),
                    Arguments.of(
                            named("delete", MQL_ITEMS_DELETE),
                            MONGO_BULK_WRITE_EXCEPTION_WITH_ERRORS,
                            BULK_WRITE_RESULT.getDeletedCount()));
        }

        @ParameterizedTest(
                name = "test executeBatch throws BatchUpdateException when MongoBulkWriteException occurs."
                        + " Parameters: commandName={0}, exception={1}")
        @MethodSource("bulkWriteExceptionsForExecuteBatch")
        void testExecuteBatchThrowsBatchUpdateExceptionWhenMongoBulkWriteExceptionOccurs(
                String mql, MongoBulkWriteException mongoBulkWriteException, int expectedUpdateCountLength)
                throws SQLException {
            doThrow(mongoBulkWriteException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            try (MongoPreparedStatement mongoPreparedStatement = createMongoPreparedStatement(mql)) {
                mongoPreparedStatement.addBatch();
                assertThatExceptionOfType(BatchUpdateException.class)
                        .isThrownBy(mongoPreparedStatement::executeBatch)
                        .withCause(mongoBulkWriteException)
                        .satisfies(batchUpdateException -> {
                            Integer vendorCodeError = getVendorCodeError(mongoBulkWriteException);
                            assertAll(
                                    () -> assertUpdateCounts(
                                            batchUpdateException.getUpdateCounts(), expectedUpdateCountLength),
                                    () -> assertNull(batchUpdateException.getSQLState()),
                                    () -> assertEquals(vendorCodeError, batchUpdateException.getErrorCode()));
                        });
            }
        }

        private void assertExecuteThrowsSqlException(
                String mql,
                SqlConsumer<MongoPreparedStatement> executeConsumer,
                Exception expectedCause,
                Class<? extends SQLException> expectedExceptionType)
                throws SQLException {
            try (MongoPreparedStatement mongoPreparedStatement = createMongoPreparedStatement(mql)) {
                assertThatExceptionOfType(expectedExceptionType)
                        .isThrownBy(() -> executeConsumer.accept(mongoPreparedStatement))
                        .withCause(expectedCause)
                        .satisfies(sqlException -> {
                            assertAll(
                                    () -> assertNull(sqlException.getSQLState()),
                                    () -> assertEquals(0, sqlException.getErrorCode()));
                        });
            }
        }

        private static Integer getVendorCodeError(final MongoBulkWriteException mongoBulkWriteException) {
            return mongoBulkWriteException.getWriteErrors().stream()
                    .map(BulkWriteError::getCode)
                    .findFirst()
                    .orElse(0);
        }

        private static void assertUpdateCounts(final int[] updateCounts, int expectedUpdateCountsLength) {
            assertEquals(expectedUpdateCountsLength, updateCounts.length);
            for (int count : updateCounts) {
                assertEquals(SUCCESS_NO_INFO, count);
            }
        }
    }

    @Test
    void testParameterIndexUnderflow() throws SQLSyntaxErrorException {
        var mongoPreparedStatement = createMongoPreparedStatement(EXAMPLE_MQL);
        checkSetterMethods(mongoPreparedStatement, 0, MongoPreparedStatementTests::assertThrowsOutOfRangeException);
    }

    @Test
    void testParameterIndexOverflow() throws SQLSyntaxErrorException {
        var mongoPreparedStatement = createMongoPreparedStatement(EXAMPLE_MQL);
        checkSetterMethods(mongoPreparedStatement, 8, MongoPreparedStatementTests::assertThrowsOutOfRangeException);
    }

    @Nested
    class ExecuteMethodClosesLastOpenResultSetTests {

        @Mock
        MongoCollection<BsonDocument> mongoCollection;

        @Mock
        AggregateIterable<BsonDocument> aggregateIterable;

        @Mock
        MongoCursor<BsonDocument> mongoCursor;

        private ResultSet lastOpenResultSet;

        private MongoPreparedStatement mongoPreparedStatement;

        @BeforeEach
        void beforeEach() throws SQLException {
            String exampleQueryMql =
                    """
                    {
                        aggregate: "books",
                        pipeline: [
                            { $match: { _id: { $eq: 1 } } },
                            { $project: { _id: 0, title: 1, publishYear: 1 } }
                        ]
                    }""";
            mongoPreparedStatement = createMongoPreparedStatement(exampleQueryMql);
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(aggregateIterable).when(mongoCollection).aggregate(same(clientSession), anyList());
            doReturn(mongoCursor).when(aggregateIterable).cursor();

            lastOpenResultSet = mongoPreparedStatement.executeQuery();
            assertFalse(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteQuery() throws SQLException {
            mongoPreparedStatement.executeQuery();
            assertTrue(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteUpdate() throws SQLException {
            assertThrows(SQLException.class, () -> mongoPreparedStatement.executeUpdate());
            assertTrue(lastOpenResultSet.isClosed());
        }

        @Test
        void testExecuteBatch() throws SQLException {
            mongoPreparedStatement.executeBatch();
            assertTrue(lastOpenResultSet.isClosed());
        }
    }

    @Test
    void testCheckClosed() throws SQLException {
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
                }
                """;
        var mongoPreparedStatement = createMongoPreparedStatement(mql);
        mongoPreparedStatement.close();
        checkMethodsWithOpenPrecondition(
                mongoPreparedStatement, MongoPreparedStatementTests::assertThrowsClosedException);
    }

    private static void checkSetterMethods(
            MongoPreparedStatement mongoPreparedStatement, int parameterIndex, Consumer<Executable> asserter) {
        var now = System.currentTimeMillis();
        var calendar = Calendar.getInstance();
        assertAll(
                () -> asserter.accept(() -> mongoPreparedStatement.setNull(parameterIndex, Types.INTEGER)),
                () -> asserter.accept(() -> mongoPreparedStatement.setBoolean(parameterIndex, true)),
                () -> asserter.accept(() -> mongoPreparedStatement.setInt(parameterIndex, 1)),
                () -> asserter.accept(() -> mongoPreparedStatement.setLong(parameterIndex, 1L)),
                () -> asserter.accept(() -> mongoPreparedStatement.setDouble(parameterIndex, 1.0)),
                () -> asserter.accept(() -> mongoPreparedStatement.setBigDecimal(parameterIndex, new BigDecimal(1))),
                () -> asserter.accept(() -> mongoPreparedStatement.setString(parameterIndex, "")),
                () -> asserter.accept(() -> mongoPreparedStatement.setBytes(parameterIndex, "".getBytes())),
                () -> asserter.accept(() -> mongoPreparedStatement.setDate(parameterIndex, new Date(now))),
                () -> asserter.accept(() -> mongoPreparedStatement.setTime(parameterIndex, new Time(now))),
                () -> asserter.accept(() -> mongoPreparedStatement.setTimestamp(parameterIndex, new Timestamp(now))),
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setObject(parameterIndex, Mockito.mock(Array.class), Types.OTHER)),
                () -> asserter.accept(() -> mongoPreparedStatement.setArray(parameterIndex, Mockito.mock(Array.class))),
                () -> asserter.accept(() -> mongoPreparedStatement.setDate(parameterIndex, new Date(now), calendar)),
                () -> asserter.accept(() -> mongoPreparedStatement.setTime(parameterIndex, new Time(now), calendar)),
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setTimestamp(parameterIndex, new Timestamp(now), calendar)));
    }

    private static void checkMethodsWithOpenPrecondition(
            MongoPreparedStatement mongoPreparedStatement, Consumer<Executable> asserter) {
        checkSetterMethods(mongoPreparedStatement, 1, asserter);
        assertAll(
                () -> asserter.accept(mongoPreparedStatement::executeQuery),
                () -> asserter.accept(mongoPreparedStatement::executeUpdate),
                () -> asserter.accept(mongoPreparedStatement::addBatch),
                () -> asserter.accept(() -> mongoPreparedStatement.setQueryTimeout(20_000)),
                () -> asserter.accept(() -> mongoPreparedStatement.setFetchSize(10)));
    }

    private static void assertThrowsOutOfRangeException(Executable executable) {
        var e = assertThrows(SQLException.class, executable);
        assertThat(e.getMessage()).startsWith("Invalid parameter index");
    }

    private static void assertThrowsClosedException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertThat(exception.getMessage()).isEqualTo("MongoPreparedStatement has been closed");
    }

    interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }
}

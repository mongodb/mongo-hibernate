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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.type.MqlType;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
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
        private ArgumentCaptor<BsonDocument> commandCaptor;

        @Test
        @DisplayName("Happy path when all parameters are provided values")
        void testSuccess() throws SQLException {

            doReturn(Document.parse("{ok: 1.0, n: 1}"))
                    .when(mongoDatabase)
                    .runCommand(eq(clientSession), any(BsonDocument.class));

            try (var preparedStatement = createMongoPreparedStatement(EXAMPLE_MQL)) {

                preparedStatement.setString(1, "s1");
                preparedStatement.setString(2, "s2");
                preparedStatement.setInt(3, 1);
                preparedStatement.setBoolean(4, true);
                preparedStatement.setString(5, "array element");
                preparedStatement.setObject(6, new ObjectId(1, 2), MqlType.OBJECT_ID.getVendorTypeNumber());
                preparedStatement.setObject(7, new ObjectId(2, 0), MqlType.OBJECT_ID.getVendorTypeNumber());

                preparedStatement.executeUpdate();

                verify(mongoDatabase).runCommand(eq(clientSession), commandCaptor.capture());
                var command = commandCaptor.getValue();
                var expectedDoc = new BsonDocument()
                        .append("insert", new BsonString("items"))
                        .append(
                                "documents",
                                new BsonArray(List.of(new BsonDocument()
                                        .append("string1", new BsonString("s1"))
                                        .append("string2", new BsonString("s2"))
                                        .append("int32", new BsonInt32(1))
                                        .append("boolean", BsonBoolean.TRUE)
                                        .append(
                                                "stringAndObjectId",
                                                new BsonArray(List.of(
                                                        new BsonString("array element"),
                                                        new BsonObjectId(new ObjectId(1, 2)))))
                                        .append("objectId", new BsonObjectId(new ObjectId(2, 0))))));
                assertEquals(expectedDoc, command);
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
            doReturn(Document.parse("{n: 10}"))
                    .when(mongoDatabase)
                    .runCommand(eq(clientSession), any(BsonDocument.class));
            mongoPreparedStatement.executeUpdate();
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
}

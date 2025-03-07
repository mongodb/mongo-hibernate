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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoDatabase;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.function.Consumer;
import org.bson.BsonDocument;
import org.bson.Document;
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
               insert: "books",
               documents: [
                   {
                       title: { $undefined: true },
                       author: { $undefined: true },
                       publishYear: { $undefined: true },
                       outOfStock: { $undefined: true },
                       tags: [
                           { $undefined: true }
                       ]
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

                preparedStatement.setString(1, "War and Peace");
                preparedStatement.setString(2, "Leo Tolstoy");
                preparedStatement.setInt(3, 1869);
                preparedStatement.setBoolean(4, false);
                preparedStatement.setString(5, "classic");

                preparedStatement.executeUpdate();

                verify(mongoDatabase).runCommand(eq(clientSession), commandCaptor.capture());
                var command = commandCaptor.getValue();
                var expectedDoc = BsonDocument.parse(
                        """
                        {
                            insert: "books",
                            documents: [
                                {
                                    title: "War and Peace",
                                    author: "Leo Tolstoy",
                                    publishYear: 1869,
                                    outOfStock: false,
                                    tags: [
                                        "classic"
                                    ]
                                }
                            ]
                        }
                        """);
                assertEquals(expectedDoc, command);
            }
        }
    }

    @Test
    void testParameterIndexUnderflow() throws SQLSyntaxErrorException {
        var mongoPreparedStatement = createMongoPreparedStatement(EXAMPLE_MQL);
        checkSetterMethods(mongoPreparedStatement, 0, this::assertThrowsOutOfRangeException);
    }

    @Test
    void testParameterIndexOverflow() throws SQLSyntaxErrorException {
        var mongoPreparedStatement = createMongoPreparedStatement(EXAMPLE_MQL);
        checkSetterMethods(mongoPreparedStatement, 6, this::assertThrowsOutOfRangeException);
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
        checkMethodsWithOpenPrecondition(mongoPreparedStatement, this::assertThrowsClosedException);
    }

    private void checkSetterMethods(
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
                () -> asserter.accept(() -> mongoPreparedStatement.setTime(parameterIndex, new Time(now), calendar)),
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setTimestamp(parameterIndex, new Timestamp(now), calendar)),
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setObject(parameterIndex, Mockito.mock(Array.class), Types.OTHER)),
                () -> asserter.accept(() -> mongoPreparedStatement.setArray(parameterIndex, Mockito.mock(Array.class))),
                () -> asserter.accept(() -> mongoPreparedStatement.setDate(parameterIndex, new Date(now), calendar)),
                () -> asserter.accept(() -> mongoPreparedStatement.setTime(parameterIndex, new Time(now), calendar)),
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setTimestamp(parameterIndex, new Timestamp(now), calendar)),
                () -> asserter.accept(() -> mongoPreparedStatement.setNull(parameterIndex, Types.STRUCT, "BOOK")));
    }

    private void checkMethodsWithOpenPrecondition(
            MongoPreparedStatement mongoPreparedStatement, Consumer<Executable> asserter) {
        checkSetterMethods(mongoPreparedStatement, 1, asserter);
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
                () -> asserter.accept(mongoPreparedStatement::executeQuery),
                () -> asserter.accept(mongoPreparedStatement::executeUpdate),
                () -> asserter.accept(mongoPreparedStatement::addBatch),
                () -> asserter.accept(() -> mongoPreparedStatement.setQueryTimeout(20_000)),
                () -> asserter.accept(() -> mongoPreparedStatement.setFetchSize(10)),
                () -> asserter.accept(() -> mongoPreparedStatement.execute(exampleUpdateMql)),
                () -> asserter.accept(() -> mongoPreparedStatement.executeQuery(exampleQueryMql)),
                () -> asserter.accept(() -> mongoPreparedStatement.executeUpdate(exampleUpdateMql)),
                () -> asserter.accept(() -> mongoPreparedStatement.addBatch(exampleUpdateMql)));
    }

    private void assertThrowsOutOfRangeException(Executable executable) {
        var e = assertThrows(SQLException.class, executable);
        assertThat(e.getMessage()).startsWith("Invalid parameter index");
    }

    private void assertThrowsClosedException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertThat(exception.getMessage()).isEqualTo("MongoPreparedStatement has been closed");
    }
}

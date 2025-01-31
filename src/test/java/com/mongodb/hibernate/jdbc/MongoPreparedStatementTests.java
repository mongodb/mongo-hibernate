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

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private MongoClient mongoClient;

    @Mock
    private ClientSession clientSession;

    @Mock
    private MongoConnection mongoConnection;

    private MongoPreparedStatement createMongoPreparedStatement(String mql) {
        return new MongoPreparedStatement(mongoClient, clientSession, mongoConnection, mql);
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

        @Mock
        private MongoDatabase mongoDatabase;

        @Captor
        private ArgumentCaptor<BsonDocument> commandCaptor;

        @Test
        @DisplayName("Happy path when all parameters are provided values")
        void testSuccess() throws SQLException {
            // given
            doReturn(mongoDatabase).when(mongoClient).getDatabase(anyString());
            doReturn(Document.parse("{ok: 1.0, n: 1}"))
                    .when(mongoDatabase)
                    .runCommand(eq(clientSession), any(BsonDocument.class));

            // when && then
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

        @Test
        @DisplayName("SQLException is thrown when parameter index is invalid")
        void testParameterIndexInvalid() {
            try (var preparedStatement = createMongoPreparedStatement(EXAMPLE_MQL)) {
                var sqlException =
                        assertThrows(SQLException.class, () -> preparedStatement.setString(0, "War and Peace"));
                assertEquals(
                        format("Parameter index invalid: %d; should be within [1, %d]", 0, 5),
                        sqlException.getMessage());
                verify(mongoClient, never()).getDatabase(anyString());
            }
        }
    }

    @Nested
    class CloseTests {

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoPreparedStatement")
        @MethodSource("getMongoPreparedStatementMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, PreparedStatementMethodInvocation methodInvocation) {
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
                     }
                     """;

            var preparedStatement = createMongoPreparedStatement(mql);
            preparedStatement.close();

            // when && then
            var sqlException = assertThrows(SQLException.class, () -> methodInvocation.runOn(preparedStatement));
            assertEquals("MongoPreparedStatement has been closed", sqlException.getMessage());
        }

        private static Stream<Arguments> getMongoPreparedStatementMethodInvocationsImpactedByClosing() {
            var now = System.currentTimeMillis();
            var calendar = Calendar.getInstance();
            return Map.<String, PreparedStatementMethodInvocation>ofEntries(
                            Map.entry("executeQuery()", MongoPreparedStatement::executeQuery),
                            Map.entry("executeUpdate()", MongoPreparedStatement::executeUpdate),
                            Map.entry("setNull(int,int)", pstmt -> pstmt.setNull(1, Types.INTEGER)),
                            Map.entry("setBoolean(int,boolean)", pstmt -> pstmt.setBoolean(1, true)),
                            Map.entry("setByte(int,byte)", pstmt -> pstmt.setByte(1, (byte) 10)),
                            Map.entry("setShort(int,short)", pstmt -> pstmt.setShort(1, (short) 10)),
                            Map.entry("setInt(int,int)", pstmt -> pstmt.setInt(1, 1)),
                            Map.entry("setLong(int,long)", pstmt -> pstmt.setLong(1, 1L)),
                            Map.entry("setFloat(int,float)", pstmt -> pstmt.setFloat(1, 1.0F)),
                            Map.entry("setDouble(int,double)", pstmt -> pstmt.setDouble(1, 1.0)),
                            Map.entry(
                                    "setBigDecimal(int,BigDecimal)",
                                    pstmt -> pstmt.setBigDecimal(1, new BigDecimal(1))),
                            Map.entry("setString(int,String)", pstmt -> pstmt.setString(1, "")),
                            Map.entry("setBytes(int,byte[])", pstmt -> pstmt.setBytes(1, "".getBytes())),
                            Map.entry("setDate(int,Date)", pstmt -> pstmt.setDate(1, new Date(now))),
                            Map.entry("setTime(int,Time)", pstmt -> pstmt.setTime(1, new Time(now))),
                            Map.entry(
                                    "setTimestamp(int,Timestamp)", pstmt -> pstmt.setTimestamp(1, new Timestamp(now))),
                            Map.entry(
                                    "setBinaryStream(int,InputStream,int)",
                                    pstmt -> pstmt.setBinaryStream(1, new ByteArrayInputStream("".getBytes()), 0)),
                            Map.entry(
                                    "setObject(int,Object,int)",
                                    pstmt -> pstmt.setObject(1, Mockito.mock(Array.class), Types.OTHER)),
                            Map.entry("addBatch()", MongoPreparedStatement::addBatch),
                            Map.entry("setArray(int,Array)", pstmt -> pstmt.setArray(1, Mockito.mock(Array.class))),
                            Map.entry("setDate(int,Date,Calendar)", pstmt -> pstmt.setDate(1, new Date(now), calendar)),
                            Map.entry("setTime(int,Time,Calendar)", pstmt -> pstmt.setTime(1, new Time(now), calendar)),
                            Map.entry(
                                    "setTimestamp(int,Timestamp,Calendar)",
                                    pstmt -> pstmt.setTimestamp(1, new Timestamp(now), calendar)),
                            Map.entry("setNull(int,Object,String)", pstmt -> pstmt.setNull(1, Types.STRUCT, "BOOK")))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }

    @Nested
    class ParameterIndexCheckingTests {

        private MongoPreparedStatement preparedStatement;

        @BeforeEach
        void setUp() {
            preparedStatement = createMongoPreparedStatement(EXAMPLE_MQL);
        }

        @AfterEach
        void tearDown() {
            preparedStatement.close();
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called with parameter index of 0")
        @MethodSource("getMongoPreparedStatementMethodInvocationsWithParameterIndexUnderflow")
        void testParameterIndexUnderflow(String label, PreparedStatementMethodInvocation methodInvocation) {
            var sqlException = assertThrows(SQLException.class, () -> methodInvocation.runOn(preparedStatement));
            assertTrue(sqlException.getMessage().startsWith("Parameter index invalid"));
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called with parameter index of 6")
        @MethodSource("getMongoPreparedStatementMethodInvocationsWithParameterIndexOverflow")
        void testParameterindexOverflow(String label, PreparedStatementMethodInvocation methodInvocation) {
            var sqlException = assertThrows(SQLException.class, () -> methodInvocation.runOn(preparedStatement));
            assertTrue(sqlException.getMessage().startsWith("Parameter index invalid"));
        }

        private static Stream<Arguments> getMongoPreparedStatementMethodInvocationsWithParameterIndexUnderflow() {
            return doGetMongoPreparedStatementMethodInvocationsWithParameterIndex(0);
        }

        private static Stream<Arguments> getMongoPreparedStatementMethodInvocationsWithParameterIndexOverflow() {
            return doGetMongoPreparedStatementMethodInvocationsWithParameterIndex(6);
        }

        private static Stream<Arguments> doGetMongoPreparedStatementMethodInvocationsWithParameterIndex(
                int parameterIndex) {
            var now = System.currentTimeMillis();
            var calendar = Calendar.getInstance();
            return Map.<String, PreparedStatementMethodInvocation>ofEntries(
                            Map.entry("setNull(int,int)", pstmt -> pstmt.setNull(parameterIndex, Types.INTEGER)),
                            Map.entry("setBoolean(int,boolean)", pstmt -> pstmt.setBoolean(parameterIndex, true)),
                            Map.entry("setByte(int,byte)", pstmt -> pstmt.setByte(parameterIndex, (byte) 10)),
                            Map.entry("setShort(int,short)", pstmt -> pstmt.setShort(parameterIndex, (short) 10)),
                            Map.entry("setInt(int,int)", pstmt -> pstmt.setInt(parameterIndex, 1)),
                            Map.entry("setLong(int,long)", pstmt -> pstmt.setLong(parameterIndex, 1L)),
                            Map.entry("setFloat(int,float)", pstmt -> pstmt.setFloat(parameterIndex, 1.0F)),
                            Map.entry("setDouble(int,double)", pstmt -> pstmt.setDouble(parameterIndex, 1.0)),
                            Map.entry(
                                    "setBigDecimal(int,BigDecimal)",
                                    pstmt -> pstmt.setBigDecimal(parameterIndex, new BigDecimal(1))),
                            Map.entry("setString(int,String)", pstmt -> pstmt.setString(parameterIndex, "")),
                            Map.entry("setBytes(int,byte[])", pstmt -> pstmt.setBytes(parameterIndex, "".getBytes())),
                            Map.entry("setDate(int,Date)", pstmt -> pstmt.setDate(parameterIndex, new Date(now))),
                            Map.entry("setTime(int,Time)", pstmt -> pstmt.setTime(parameterIndex, new Time(now))),
                            Map.entry(
                                    "setTimestamp(int,Timestamp)",
                                    pstmt -> pstmt.setTimestamp(parameterIndex, new Timestamp(now))),
                            Map.entry(
                                    "setBinaryStream(int,InputStream,int)",
                                    pstmt -> pstmt.setBinaryStream(
                                            parameterIndex, new ByteArrayInputStream("".getBytes()), 0)),
                            Map.entry(
                                    "setObject(int,Object,int)",
                                    pstmt -> pstmt.setObject(parameterIndex, Mockito.mock(Array.class), Types.OTHER)),
                            Map.entry(
                                    "setArray(int,Array)",
                                    pstmt -> pstmt.setArray(parameterIndex, Mockito.mock(Array.class))),
                            Map.entry(
                                    "setDate(int,Date,Calendar)",
                                    pstmt -> pstmt.setDate(parameterIndex, new Date(now), calendar)),
                            Map.entry(
                                    "setTime(int,Time,Calendar)",
                                    pstmt -> pstmt.setTime(parameterIndex, new Time(now), calendar)),
                            Map.entry(
                                    "setTimestamp(int,Timestamp,Calendar)",
                                    pstmt -> pstmt.setTimestamp(parameterIndex, new Timestamp(now), calendar)),
                            Map.entry(
                                    "setNull(int,Object,String)",
                                    pstmt -> pstmt.setNull(parameterIndex, Types.STRUCT, "BOOK")))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }

    @FunctionalInterface
    interface PreparedStatementMethodInvocation {
        void runOn(MongoPreparedStatement pstmt) throws SQLException;
    }
}

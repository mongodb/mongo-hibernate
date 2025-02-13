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
import static org.bson.BsonDocument.parse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.BsonDocument;
import org.bson.Document;
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

    private MongoPreparedStatement createMongoPreparedStatement(String mql) throws SQLSyntaxErrorException {
        return new MongoPreparedStatement(mongoClient, clientSession, mongoConnection, mql);
    }

    @Nested
    class ParameterValueSettingTests {

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
                var expectedDoc = parse(
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
        void testParameterIndexInvalid() throws SQLSyntaxErrorException {
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
        void testCheckClosed(String label, PreparedStatementMethodInvocation methodInvocation)
                throws SQLSyntaxErrorException {
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
                            Map.entry("setArray(int,Array)", pstmt -> pstmt.setArray(1, Mockito.mock(Array.class))),
                            Map.entry("setDate(int,Date,Calendar)", pstmt -> pstmt.setDate(1, new Date(now), calendar)),
                            Map.entry("setTime(int,Time,Calendar)", pstmt -> pstmt.setTime(1, new Time(now), calendar)),
                            Map.entry(
                                    "setTimestamp(int,Timestamp,Calendar)",
                                    pstmt -> pstmt.setTimestamp(1, new Timestamp(now), calendar)),
                            Map.entry("setNull(int,Object,String)", pstmt -> pstmt.setNull(1, Types.STRUCT, "BOOK")),
                            Map.entry("addBatch()", MongoPreparedStatement::addBatch),
                            Map.entry("clearBatch()", MongoPreparedStatement::clearBatch),
                            Map.entry("executeBatch()", MongoPreparedStatement::executeBatch))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }

    @Nested
    class BatchTests {

        @Mock
        private MongoDatabase mongoDatabase;

        @Mock
        private MongoCollection<BsonDocument> mongoCollection;

        @Mock
        private BulkWriteResult bulkWriteResult;

        @Captor
        private ArgumentCaptor<List<WriteModel<BsonDocument>>> bulkWriteModelsCaptor;

        private static final String EXAMPLE_MQL =
                """
                 {
                    insert: "books",
                    documents: [
                        {
                            title: { $undefined: true },
                            publishYear: { $undefined: true }
                        }
                    ]
                 }""";

        @Test
        void testSuccess() throws SQLException {

            // given
            doReturn(mongoDatabase).when(mongoClient).getDatabase(anyString());
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(bulkWriteResult).when(mongoCollection).bulkWrite(eq(clientSession), anyList());

            // when
            try (var pstmt = createMongoPreparedStatement(EXAMPLE_MQL)) {
                try {
                    pstmt.setString(1, "War and Peace");
                    pstmt.setInt(2, 1869);
                    pstmt.addBatch();

                    pstmt.setString(1, "Crime and Punishment");
                    pstmt.setInt(2, 1866);
                    pstmt.addBatch();

                    pstmt.executeBatch();

                    pstmt.setString(1, "Fathers and Sons");
                    pstmt.setInt(2, 1862);
                    pstmt.addBatch();

                    pstmt.executeBatch();
                } finally {
                    pstmt.clearBatch();
                }

                // then
                Mockito.verify(mongoCollection, times(2)).bulkWrite(eq(clientSession), bulkWriteModelsCaptor.capture());
                var batches = bulkWriteModelsCaptor.getAllValues();

                assertEquals(2, batches.size());

                var firstBatch = batches.get(0);
                assertEquals(2, firstBatch.size());
                assertInsertOneModel(
                        firstBatch.get(0),
                        parse(
                                """
                            {
                                title: "War and Peace",
                                publishYear: 1869
                            }"""));

                assertInsertOneModel(
                        firstBatch.get(1),
                        parse(
                                """
                            {
                                title: "Crime and Punishment",
                                publishYear: 1866
                            }"""));

                var secondBatch = batches.get(1);
                assertEquals(1, secondBatch.size());

                assertInsertOneModel(
                        secondBatch.get(0),
                        parse(
                                """
                            {
                                title: "Fathers and Sons",
                                publishYear: 1862
                            }"""));
            }
        }

        @Test
        void testClearBatch() throws SQLException {
            // when
            try (var pstmt = createMongoPreparedStatement(EXAMPLE_MQL)) {
                pstmt.setString(1, "War and Peace");
                pstmt.setInt(2, 1869);
                pstmt.addBatch();

                pstmt.clearBatch();
                var rowCounts = pstmt.executeBatch();

                // then
                assertEquals(0, rowCounts.length);
            }
        }

        @ParameterizedTest
        @MethodSource("getBulkWriteModelsArguments")
        void testBulkWriteModels(String mql, List<? extends WriteModel<BsonDocument>> expectedWriteModels)
                throws SQLException {
            // given
            doReturn(mongoDatabase).when(mongoClient).getDatabase(anyString());
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(bulkWriteResult).when(mongoCollection).bulkWrite(eq(clientSession), anyList());

            try (var pstmt = createMongoPreparedStatement(mql)) {
                try {
                    pstmt.addBatch();
                    pstmt.executeBatch();

                    // then
                    Mockito.verify(mongoCollection).bulkWrite(eq(clientSession), bulkWriteModelsCaptor.capture());
                    var batches = bulkWriteModelsCaptor.getAllValues();
                    assertEquals(1, batches.size());
                    var actualWriteModels = batches.get(0);
                    assertEquals(expectedWriteModels.size(), actualWriteModels.size());
                    for (var i = 0; i < expectedWriteModels.size(); i++) {
                        assertWriteModelsEqual(expectedWriteModels.get(i), actualWriteModels.get(i));
                    }
                } finally {
                    pstmt.clearBatch();
                    var result = pstmt.executeBatch();
                    assertEquals(0, result.length);
                }
            }
        }

        private static Stream<Arguments> getBulkWriteModelsArguments() {
            return Map.ofEntries(
                            Map.entry(
                                    """
                                    {
                                        insert: "books",
                                        documents: [
                                            { _id: 1 },
                                            { _id: 2 }
                                        ]
                                    }""",
                                    List.of(
                                            new InsertOneModel<>(parse("{ _id: 1 }")),
                                            new InsertOneModel<>(parse("{ _id: 2 }")))),
                            Map.entry(
                                    """
                                    {
                                        update: "books",
                                        updates: [
                                            {
                                                q: { _id: 1 },
                                                u: {
                                                    $set: {
                                                        title: "War and Peace"
                                                    }
                                                },
                                                multi: false
                                            },
                                            {
                                                q: { author: "Leo Tolstoy" },
                                                u: {
                                                    $set: {
                                                        borrowed: true
                                                    }
                                                },
                                                multi: true
                                            }
                                        ]
                                    }""",
                                    List.of(
                                            new UpdateOneModel<>(
                                                    parse("{ _id: 1 }"),
                                                    parse("{ $set: { title: \"War and Peace\" } }")),
                                            new UpdateManyModel<>(
                                                    parse("{ author: \"Leo Tolstoy\" }"),
                                                    parse("{ $set: { borrowed: true } }")))),
                            Map.entry(
                                    """
                                    {
                                        delete: "books",
                                        deletes: [
                                            { q: { _id: 1 }, limit: 1 },
                                            { q: {}, limit: 0 }
                                        ]
                                    }
                                    """,
                                    List.of(
                                            new DeleteOneModel<>(parse("{ _id: 1 }")),
                                            new DeleteManyModel<>(parse("{}")))))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }

        private void assertInsertOneModel(WriteModel<BsonDocument> writeModel, BsonDocument expectedDoc) {
            assertInstanceOf(InsertOneModel.class, writeModel);
            assertEquals(expectedDoc, ((InsertOneModel<?>) writeModel).getDocument());
        }

        private void assertWriteModelsEqual(
                WriteModel<BsonDocument> writeModel1, WriteModel<BsonDocument> writeModel2) {
            assertSame(writeModel1.getClass(), writeModel2.getClass());
            if (writeModel1 instanceof InsertOneModel<BsonDocument> insertOneModel1) {
                assertEquals(insertOneModel1.getDocument(), ((InsertOneModel<BsonDocument>) writeModel2).getDocument());
            } else if (writeModel1 instanceof UpdateOneModel<BsonDocument> updateOneModel1) {
                var updateOneModel2 = (UpdateOneModel<BsonDocument>) writeModel2;
                assertEquals(updateOneModel1.getFilter(), updateOneModel2.getFilter());
                assertEquals(updateOneModel1.getUpdate(), updateOneModel2.getUpdate());
            } else if (writeModel1 instanceof UpdateManyModel<BsonDocument> updateManyModel1) {
                var updateManyModel2 = (UpdateManyModel<BsonDocument>) writeModel2;
                assertEquals(updateManyModel1.getFilter(), updateManyModel2.getFilter());
                assertEquals(updateManyModel1.getUpdate(), updateManyModel2.getUpdate());
            } else if (writeModel1 instanceof DeleteOneModel<BsonDocument> deleteOneModel1) {
                var deleteOneModel2 = (DeleteOneModel<BsonDocument>) writeModel2;
                assertEquals(deleteOneModel1.getFilter(), deleteOneModel2.getFilter());
            } else if (writeModel1 instanceof DeleteManyModel<BsonDocument> deleteManyModel1) {
                var deleteManyModel2 = (DeleteManyModel<BsonDocument>) writeModel2;
                assertEquals(deleteManyModel1.getFilter(), deleteManyModel2.getFilter());
            } else {
                throw new IllegalStateException("unexpected WriteModel: " + writeModel1.getClass());
            }
        }
    }

    @FunctionalInterface
    interface PreparedStatementMethodInvocation {
        void runOn(MongoPreparedStatement pstmt) throws SQLException;
    }
}

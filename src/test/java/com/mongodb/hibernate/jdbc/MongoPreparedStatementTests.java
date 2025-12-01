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

import static com.mongodb.hibernate.internal.MongoConstants.EXTENDED_JSON_WRITER_SETTINGS;
import static com.mongodb.hibernate.jdbc.MongoStatement.NO_ERROR_CODE;
import static java.sql.Statement.SUCCESS_NO_INFO;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatObject;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.of;
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
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.WriteConcernError;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLSyntaxErrorException;
import java.sql.Types;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.bson.BSONException;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    void testNoCommandNameProvidedExecuteQuery() throws SQLException {
        assertInvalidMql(
                """
                {}""",
                PreparedStatement::executeQuery,
                "Invalid MQL. Command name is missing: [{}]");
    }

    @Test
    void testNoCommandNameProvidedExecuteUpdate() throws SQLException {
        assertInvalidMql(
                """
                {}""",
                PreparedStatement::executeUpdate,
                "Invalid MQL. Command name is missing: [{}]");
    }

    @Test
    void testNoCommandNameProvidedExecuteBatch() throws SQLException {
        assertInvalidMql(
                """
                {}""",
                preparedStatement -> {
                    preparedStatement.addBatch();
                    preparedStatement.executeBatch();
                },
                "Invalid MQL. Command name is missing: [{}]");
    }

    @Test
    void testNoCollectionNameProvidedExecuteQuery() throws SQLException {
        assertInvalidMql(
                """
                {
                    aggregate: {}
                }""",
                PreparedStatement::executeQuery,
                """
                Invalid MQL. Collection name is missing [{"aggregate": {}}]""");
    }

    @Test
    void testNoCollectionNameProvidedExecuteUpdate() throws SQLException {
        assertInvalidMql(
                """
                {
                    insert: {}
                }""",
                PreparedStatement::executeUpdate,
                """
                Invalid MQL. Collection name is missing [{"insert": {}}]""");
    }

    @Test
    void testNoCollectionNameProvidedExecuteBatch() throws SQLException {
        assertInvalidMql(
                """
                {
                    insert: {}
                }""",
                preparedStatement -> {
                    preparedStatement.addBatch();
                    preparedStatement.executeBatch();
                },
                """
                Invalid MQL. Collection name is missing [{"insert": {}}]""");
    }

    @Test
    void testMissingRequiredAggregateCommandField() throws SQLException {
        var mql = """
                  {
                      aggregate: "books"
                  }""";
        try (var pstm = createMongoPreparedStatement(mql)) {
            assertThatThrownBy(pstm::executeQuery)
                    .isInstanceOf(SQLSyntaxErrorException.class)
                    .hasMessage("Invalid MQL: [%s]".formatted(toExtendedJson(mql)))
                    .cause()
                    .isInstanceOf(BSONException.class)
                    .hasMessage("Document does not contain key pipeline");
        }
    }

    @Test
    void testMissingRequiredProjectAggregationPipelineStage() throws SQLException {
        var mql =
                """
                    {
                    aggregate: "books",
                    "pipeline": []
                }""";
        try (var pstm = createMongoPreparedStatement(mql)) {
            assertThatThrownBy(pstm::executeQuery)
                    .isInstanceOf(SQLSyntaxErrorException.class)
                    .hasMessage("Invalid MQL. $project stage is missing [%s]".formatted(toExtendedJson(mql)));
        }
    }

    @Nested
    class ParameterValueSettingTests {

        @Captor
        private ArgumentCaptor<List<WriteModel<BsonDocument>>> commandCaptor;

        @Test
        @DisplayName("Happy path when all parameters are provided values")
        void testSuccess() throws SQLException {
            var bulkWriteResult = Mockito.mock(BulkWriteResult.class);

            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
            doReturn(bulkWriteResult).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            doReturn(1).when(bulkWriteResult).getInsertedCount();

            try (var preparedStatement = createMongoPreparedStatement(EXAMPLE_MQL)) {

                preparedStatement.setString(1, "s1");
                preparedStatement.setString(2, "s2");
                preparedStatement.setInt(3, 1);
                preparedStatement.setBoolean(4, true);
                preparedStatement.setString(5, "array element");
                preparedStatement.setObject(6, new ObjectId(1, 2), ObjectIdJdbcType.SQL_TYPE.getVendorTypeNumber());
                preparedStatement.setObject(7, new ObjectId(2, 0), ObjectIdJdbcType.SQL_TYPE.getVendorTypeNumber());

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
                var insertOneModel = assertInstanceOf(InsertOneModel.class, writeModels.get(0));
                assertEquals(expectedDoc, insertOneModel.getDocument());
            }
        }
    }

    @Nested
    class ExecuteThrowsSqlFeatureNotSupportedExceptionOrSqlSyntaxErrorExceptionTests {
        @ParameterizedTest(name = "test not supported command {0}")
        @ValueSource(strings = {"findAndModify", "aggregate", "bulkWrite"})
        void testNotSupportedCommands(String commandName) throws SQLException {
            try (var pstm = createMongoPreparedStatement(
                    """
                    {
                      %s: "books"
                    }"""
                            .formatted(commandName))) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLFeatureNotSupportedException.class)
                        .hasMessageContaining(commandName);
            }
        }

        @ParameterizedTest(name = "test not supported update command field {0}")
        @ValueSource(
                strings = {
                    "maxTimeMS: 1",
                    "writeConcern: {}",
                    "bypassDocumentValidation: true",
                    "comment: {}",
                    "ordered: true",
                    "let: {}"
                })
        void testNotSupportedUpdateCommandField(String unsupportedField) throws SQLException {
            try (var pstm = createMongoPreparedStatement(
                    """
                        {
                        update: "books",
                        updates: [
                            {
                                q: { author: { $eq: "Leo Tolstoy" } },
                                u: { $set: { outOfStock: true } },
                                multi: true
                            }
                        ],
                        %s
                    }"""
                            .formatted(unsupportedField))) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLFeatureNotSupportedException.class)
                        .hasMessage("Unsupported field in [update] command: [%s]"
                                .formatted(getFieldName(unsupportedField)));
            }
        }

        @Test
        void testMissingRequiredUpdateCommandField() throws SQLException {
            var mql = """
                      {
                          update: "books"
                      }""";
            try (var pstm = createMongoPreparedStatement(mql)) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLSyntaxErrorException.class)
                        .hasMessage("Invalid MQL: [%s]".formatted(toExtendedJson(mql)))
                        .cause()
                        .hasMessage("Document does not contain key updates");
            }
        }

        @ParameterizedTest(name = "test not supported delete command field {0}")
        @ValueSource(strings = {"maxTimeMS: 1", "writeConcern: {}", "comment: {}", "ordered: true", "let: {}"})
        void testNotSupportedDeleteCommandField(String unsupportedField) throws SQLException {
            try (var pstm = createMongoPreparedStatement(
                    """
                        {
                        delete: "books",
                        deletes: [
                            {
                                q: { author: { $eq: "Leo Tolstoy" } },
                                limit: 0
                            }
                        ]
                         %s
                    }"""
                            .formatted(unsupportedField))) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLFeatureNotSupportedException.class)
                        .hasMessage("Unsupported field in [delete] command: [%s]"
                                .formatted(getFieldName(unsupportedField)));
            }
        }

        @Test
        void testMissingRequiredDeleteCommandField() throws SQLException {
            var mql = """
                        {
                        delete: "books"
                    }""";
            try (var pstm = createMongoPreparedStatement(mql)) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLSyntaxErrorException.class)
                        .hasMessage("Invalid MQL: [%s]".formatted(toExtendedJson(mql)))
                        .cause()
                        .hasMessage("Document does not contain key deletes");
            }
        }

        @ParameterizedTest(name = "test not supported insert command field {0}")
        @ValueSource(
                strings = {
                    "maxTimeMS: 1",
                    "writeConcern: {}",
                    "bypassDocumentValidation: true",
                    "comment: {}",
                    "ordered: true",
                    "let: {}"
                })
        void testNotSupportedInsertCommandField(String unsupportedField) throws SQLException {
            try (var pstm = createMongoPreparedStatement(
                    """
                        {
                        insert: "books",
                        documents: [
                            {
                                _id: 1
                            }
                        ],
                        %s
                    }"""
                            .formatted(unsupportedField))) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLFeatureNotSupportedException.class)
                        .hasMessage("Unsupported field in [insert] command: [%s]"
                                .formatted(getFieldName(unsupportedField)));
            }
        }

        @Test
        void testMissingRequiredInsertCommandField() throws SQLException {
            var mql = """
                        {
                        insert: "books"
                    }""";
            try (var pstm = createMongoPreparedStatement(mql)) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLSyntaxErrorException.class)
                        .hasMessage("Invalid MQL: [%s]".formatted(toExtendedJson(mql)))
                        .cause()
                        .hasMessage("Document does not contain key documents");
            }
        }

        private static Stream<Arguments> testNotSupportedUpdateStatemenField() {
            return Stream.of(
                    of("hint: {}", "Unsupported field in [update] statement: [hint]"),
                    of("hint: \"a\"", "Unsupported field in [update] statement: [hint]"),
                    of("collation: {}", "Unsupported field in [update] statement: [collation]"),
                    of("arrayFilters: []", "Unsupported field in [update] statement: [arrayFilters]"),
                    of("sort: {}", "Unsupported field in [update] statement: [sort]"),
                    of("upsert: true", "Unsupported field in [update] statement: [upsert]"),
                    of("u: []", "Only document type is supported as value for field: [u]"),
                    of("c: {}", "Unsupported field in [update] statement: [c]"));
        }

        @ParameterizedTest(name = "test not supported update statement field {0}")
        @MethodSource
        void testNotSupportedUpdateStatemenField(String unsupportedField, String expectedMessage) throws SQLException {
            try (var pstm = createMongoPreparedStatement(
                    """
                        {
                        update: "books",
                        updates: [
                            {
                                q: { author: { $eq: "Leo Tolstoy" } },
                                u: { $set: { outOfStock: true } },
                                multi: true,
                                %s
                            }
                        ]
                    }"""
                            .formatted(unsupportedField))) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLFeatureNotSupportedException.class)
                        .hasMessage(expectedMessage);
            }
        }

        @ParameterizedTest(name = "test missing required update statement field {0}")
        @ValueSource(strings = {"q", "u"})
        void testMissingRequiredUpdateStatementField(String missingFieldName) throws SQLException {
            var mqlDocument = BsonDocument.parse(
                    """
                        {
                        update: "books",
                        updates: [
                            {
                                q: {},
                                u: {},
                            }
                        ]
                    }""");
            mqlDocument.getArray("updates").get(0).asDocument().remove(missingFieldName);
            var mql = mqlDocument.toJson();
            try (var pstm = createMongoPreparedStatement(mql)) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLSyntaxErrorException.class)
                        .hasMessage("Invalid MQL: [%s]".formatted(toExtendedJson(mql)))
                        .cause()
                        .hasMessage("Document does not contain key %s".formatted(missingFieldName));
            }
        }

        @ParameterizedTest(name = "test not supported delete statement field {0}")
        @ValueSource(strings = {"hint: {}", "hint: \"a\"", "collation: {}"})
        void testNotSupportedDeleteStatementField(String unsupportedField) throws SQLException {
            try (var pstm = createMongoPreparedStatement(
                    """
                        {
                        delete: "books",
                        deletes: [
                            {
                                q: { author: { $eq: "Leo Tolstoy" } },
                                limit: 0,
                                %s
                            }
                        ]
                    }"""
                            .formatted(unsupportedField))) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLFeatureNotSupportedException.class)
                        .hasMessage("Unsupported field in [delete] statement: [%s]"
                                .formatted(getFieldName(unsupportedField)));
            }
        }

        @ParameterizedTest(name = "test missing required update statement field {0}")
        @ValueSource(strings = {"q", "limit"})
        void testMissingRequiredDeleteStatementField(String missingFieldName) throws SQLException {
            var mqlDocument = BsonDocument.parse(
                    """
                        {
                        delete: "books",
                        deletes: [
                            {
                                q: {},
                                limit: 0,
                            }
                        ]
                    }""");
            mqlDocument.getArray("deletes").get(0).asDocument().remove(missingFieldName);
            var mql = mqlDocument.toJson();
            try (var pstm = createMongoPreparedStatement(mql)) {
                assertThatThrownBy(pstm::executeUpdate)
                        .isInstanceOf(SQLSyntaxErrorException.class)
                        .hasMessage("Invalid MQL: [%s]".formatted(toExtendedJson(mql)))
                        .cause()
                        .hasMessage("Document does not contain key %s".formatted(missingFieldName));
            }
        }

        private static String getFieldName(String field) {
            return BsonDocument.parse("{" + field + "}").getFirstKey();
        }
    }

    @Nested
    class ExecuteThrowsSqlExceptionTests {
        private static final String DUMMY_EXCEPTION_MESSAGE = "Test message";
        private static final ServerAddress DUMMY_SERVER_ADDRESS = new ServerAddress();
        private static final BsonDocument DUMMY_ERROR_DETAILS = new BsonDocument();
        private static final BulkWriteResult BULK_WRITE_RESULT =
                BulkWriteResult.acknowledged(0, 0, 0, 0, emptyList(), emptyList());
        private static final MongoBulkWriteException MONGO_BULK_WRITE_EXCEPTION_WITH_WRITE_ERRORS =
                new MongoBulkWriteException(
                        BULK_WRITE_RESULT,
                        List.of(new BulkWriteError(10, DUMMY_EXCEPTION_MESSAGE, DUMMY_ERROR_DETAILS, 0)),
                        null,
                        DUMMY_SERVER_ADDRESS,
                        emptySet());
        private static final MongoBulkWriteException MONGO_BULK_WRITE_EXCEPTION_WITH_WRITE_CONCERN_EXCEPTION =
                new MongoBulkWriteException(
                        BULK_WRITE_RESULT,
                        emptyList(),
                        new WriteConcernError(10, "No code name", DUMMY_EXCEPTION_MESSAGE, DUMMY_ERROR_DETAILS),
                        DUMMY_SERVER_ADDRESS,
                        emptySet());

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
                        { _id: 2 }
                        { _id: 3 }
                        { _id: 4 }
                    ]
                }
                """;
        private static final String MQL_ITEMS_UPDATE =
                """
                {
                    update: "items",
                    updates: [
                        { q: { _id: 1 }, u: { $set: { touched: true } }, multi: false }
                        { q: { _id: 1 }, u: { $set: { touched: true } }, multi: false }
                        { q: { _id: 1 }, u: { $set: { touched: true } }, multi: false }
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
                        { q: { _id: 1 }, limit: 1 }
                        { q: { _id: 1 }, limit: 1 }
                        { q: { _id: 1 }, limit: 1 }
                    ]
                }
                """;

        @BeforeEach
        void beforeEach() {
            doReturn(mongoCollection).when(mongoDatabase).getCollection(anyString(), eq(BsonDocument.class));
        }

        private static Stream<Named<String>> mqlCommands() {
            return Stream.of(
                    named("insert", MQL_ITEMS_INSERT),
                    named("update", MQL_ITEMS_UPDATE),
                    named("delete", MQL_ITEMS_DELETE));
        }

        private static Stream<MongoException> timeoutExceptions() {
            var dummyCause = new RuntimeException();
            return Stream.of(
                    new MongoExecutionTimeoutException(1, DUMMY_EXCEPTION_MESSAGE),
                    new MongoSocketReadTimeoutException(DUMMY_EXCEPTION_MESSAGE, DUMMY_SERVER_ADDRESS, dummyCause),
                    new MongoSocketWriteTimeoutException(DUMMY_EXCEPTION_MESSAGE, DUMMY_SERVER_ADDRESS, dummyCause),
                    new MongoTimeoutException(DUMMY_EXCEPTION_MESSAGE),
                    new MongoOperationTimeoutException(DUMMY_EXCEPTION_MESSAGE),
                    new MongoException(50, DUMMY_EXCEPTION_MESSAGE) // 50 is a timeout error code
                    );
        }

        private static Stream<MongoException> genericMongoExceptions() {
            return Stream.of(
                    new MongoException(-3, DUMMY_EXCEPTION_MESSAGE),
                    new MongoException(11000, DUMMY_EXCEPTION_MESSAGE),
                    new MongoException(5000, DUMMY_EXCEPTION_MESSAGE));
        }

        @ParameterizedTest(name = "test executeBatch MongoException {0}")
        @MethodSource({"genericMongoExceptions", "timeoutExceptions"})
        void testExecuteBatchMongoException(MongoException mongoException) throws SQLException {
            doThrow(mongoException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            assertExecuteBatchThrowsSqlException(
                    sqlException -> assertGenericMongoException(sqlException, mongoException));
        }

        @ParameterizedTest(name = "test executeUpdate MongoException {0}")
        @MethodSource({"genericMongoExceptions", "timeoutExceptions"})
        void testExecuteUpdateMongoException(MongoException mongoException) throws SQLException {
            doThrow(mongoException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            assertExecuteUpdateThrowsSqlException(
                    sqlException -> assertGenericMongoException(sqlException, mongoException));
        }

        @ParameterizedTest(name = "test executeUQuery MongoException {0}")
        @MethodSource({"genericMongoExceptions", "timeoutExceptions"})
        void testExecuteQueryMongoException(MongoException mongoException) throws SQLException {
            doThrow(mongoException).when(mongoCollection).aggregate(eq(clientSession), anyList());
            assertExecuteQueryThrowsSqlException(
                    sqlException -> assertGenericMongoException(sqlException, mongoException));
        }

        @Test
        void testExecuteBatchRuntimeExceptionCause() throws SQLException {
            var runtimeException = new RuntimeException();
            doThrow(runtimeException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            assertExecuteBatchThrowsSqlException(
                    sqlException -> assertGenericException(sqlException, runtimeException));
        }

        @Test
        void testExecuteUpdateRuntimeExceptionCause() throws SQLException {
            var runtimeException = new RuntimeException();
            doThrow(runtimeException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            assertExecuteUpdateThrowsSqlException(
                    sqlException -> assertGenericException(sqlException, runtimeException));
        }

        @Test
        void testExecuteQueryRuntimeExceptionCause() throws SQLException {
            var runtimeException = new RuntimeException();
            doThrow(runtimeException).when(mongoCollection).aggregate(eq(clientSession), anyList());
            assertExecuteQueryThrowsSqlException(
                    sqlException -> assertGenericException(sqlException, runtimeException));
        }

        private static Stream<Arguments> testExecuteUpdateMongoBulkWriteException() {
            return mqlCommands()
                    .flatMap(mqlCommand -> Stream.of(
                            Arguments.of(mqlCommand, MONGO_BULK_WRITE_EXCEPTION_WITH_WRITE_CONCERN_EXCEPTION),
                            Arguments.of(mqlCommand, MONGO_BULK_WRITE_EXCEPTION_WITH_WRITE_ERRORS)));
        }

        @ParameterizedTest(
                name = "test executeUpdate MongoBulkWriteException. Parameters: commandName={0}, exception={1}")
        @MethodSource
        void testExecuteUpdateMongoBulkWriteException(String mql, MongoBulkWriteException mongoBulkWriteException)
                throws SQLException {
            doThrow(mongoBulkWriteException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            var vendorErrorCode = getVendorErrorCode(mongoBulkWriteException);

            try (var mongoPreparedStatement = createMongoPreparedStatement(mql)) {
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(mongoPreparedStatement::executeUpdate)
                        .returns(vendorErrorCode, SQLException::getErrorCode)
                        .returns(null, SQLException::getSQLState)
                        .havingCause()
                        .isSameAs(mongoBulkWriteException);
            }
        }

        private static Stream<Arguments> testExecuteBatchMongoBulkWriteException() {
            return mqlCommands()
                    .flatMap(mqlCommand -> Stream.of(
                            // Error in command 1
                            Arguments.of(
                                    mqlCommand, // MQL command to execute
                                    createMongoBulkWriteException(0), // failed model index
                                    0), // expected update count length
                            Arguments.of(mqlCommand, createMongoBulkWriteException(1), 0),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(2), 0),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(3), 0),

                            // Error in command 2
                            Arguments.of(mqlCommand, createMongoBulkWriteException(4), 1),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(5), 1),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(6), 1),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(7), 1),

                            // Error in command 3
                            Arguments.of(mqlCommand, createMongoBulkWriteException(8), 2),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(9), 2),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(10), 2),
                            Arguments.of(mqlCommand, createMongoBulkWriteException(11), 2),
                            Arguments.of(mqlCommand, MONGO_BULK_WRITE_EXCEPTION_WITH_WRITE_CONCERN_EXCEPTION, 0)));
        }

        @ParameterizedTest(
                name =
                        "test executeBatch MongoBulkWriteException. Parameters: commandName={0}, exception={1}, expectedUpdateCountLength={2}")
        @MethodSource
        void testExecuteBatchMongoBulkWriteException(
                String mql, MongoBulkWriteException mongoBulkWriteException, int expectedUpdateCountLength)
                throws SQLException {
            doThrow(mongoBulkWriteException).when(mongoCollection).bulkWrite(eq(clientSession), anyList());
            var vendorErrorCode = getVendorErrorCode(mongoBulkWriteException);

            try (var mongoPreparedStatement = createMongoPreparedStatement(mql)) {
                mongoPreparedStatement.addBatch();
                mongoPreparedStatement.addBatch();
                mongoPreparedStatement.addBatch();

                assertThatExceptionOfType(BatchUpdateException.class)
                        .isThrownBy(mongoPreparedStatement::executeBatch)
                        .returns(vendorErrorCode, BatchUpdateException::getErrorCode)
                        .returns(null, BatchUpdateException::getSQLState)
                        .satisfies(ex -> {
                            assertUpdateCounts(ex.getUpdateCounts(), expectedUpdateCountLength);
                        })
                        .havingCause()
                        .isSameAs(mongoBulkWriteException);
            }
        }

        private static void assertGenericException(SQLException sqlException, RuntimeException cause) {
            assertThatObject(sqlException)
                    .isExactlyInstanceOf(SQLException.class)
                    .returns(NO_ERROR_CODE, SQLException::getErrorCode)
                    .returns(null, SQLException::getSQLState)
                    .returns(cause, SQLException::getCause);
        }

        private static void assertGenericMongoException(SQLException sqlException, MongoException cause) {
            assertThatObject(sqlException)
                    .isExactlyInstanceOf(SQLException.class)
                    .returns(cause.getCode(), SQLException::getErrorCode)
                    .returns(null, SQLException::getSQLState)
                    .returns(cause, SQLException::getCause);
        }

        private void assertExecuteBatchThrowsSqlException(ThrowingConsumer<SQLException> asserter) throws SQLException {
            try (var mongoPreparedStatement = createMongoPreparedStatement(MQL_ITEMS_INSERT)) {
                mongoPreparedStatement.addBatch();
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(mongoPreparedStatement::executeBatch)
                        .isExactlyInstanceOf(SQLException.class)
                        .satisfies(asserter);
            }
        }

        private void assertExecuteUpdateThrowsSqlException(ThrowingConsumer<SQLException> asserter)
                throws SQLException {
            try (var mongoPreparedStatement = createMongoPreparedStatement(MQL_ITEMS_INSERT)) {
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(mongoPreparedStatement::executeUpdate)
                        .satisfies(asserter);
            }
        }

        private void assertExecuteQueryThrowsSqlException(ThrowingConsumer<SQLException> asserter) throws SQLException {
            try (var mongoPreparedStatement = createMongoPreparedStatement(MQL_ITEMS_AGGREGATE)) {
                assertThatExceptionOfType(SQLException.class)
                        .isThrownBy(mongoPreparedStatement::executeQuery)
                        .satisfies(asserter);
            }
        }

        private static void assertUpdateCounts(int[] actualUpdateCounts, int expectedUpdateCountsLength) {
            assertEquals(expectedUpdateCountsLength, actualUpdateCounts.length);
            for (var count : actualUpdateCounts) {
                assertEquals(SUCCESS_NO_INFO, count);
            }
        }

        private static MongoBulkWriteException createMongoBulkWriteException(int failedModelIndex) {
            return new MongoBulkWriteException(
                    BULK_WRITE_RESULT,
                    List.of(new BulkWriteError(1, DUMMY_EXCEPTION_MESSAGE, DUMMY_ERROR_DETAILS, failedModelIndex)),
                    null,
                    DUMMY_SERVER_ADDRESS,
                    emptySet());
        }

        private static int getVendorErrorCode(MongoBulkWriteException mongoBulkWriteException) {
            return mongoBulkWriteException.getWriteErrors().stream()
                    .map(BulkWriteError::getCode)
                    .findFirst()
                    .orElse(NO_ERROR_CODE);
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
    class ExecuteClosesLastOpenResultSetTests {

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
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setObject(parameterIndex, Mockito.mock(Array.class), Types.OTHER)),
                () -> asserter.accept(
                        () -> mongoPreparedStatement.setArray(parameterIndex, Mockito.mock(Array.class))));
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

    private void assertInvalidMql(String mql, SqlConsumer<PreparedStatement> executor, String expectedExceptionMessage)
            throws SQLException {
        try (var pstm = createMongoPreparedStatement(mql)) {
            assertThatThrownBy(() -> executor.accept(pstm))
                    .isInstanceOf(SQLSyntaxErrorException.class)
                    .hasMessage(expectedExceptionMessage);
        }
    }

    private static String toExtendedJson(String mql) {
        return BsonDocument.parse(mql).toJson(EXTENDED_JSON_WRITER_SETTINGS);
    }

    private interface SqlConsumer<T> {
        void accept(T t) throws SQLException;
    }
}

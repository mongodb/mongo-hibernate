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

import static com.mongodb.hibernate.jdbc.MongoDatabaseMetaData.MONGO_DATABASE_PRODUCT_NAME;
import static com.mongodb.hibernate.jdbc.MongoDatabaseMetaData.MONGO_JDBC_DRIVER_NAME;
import static java.sql.ResultSet.CONCUR_READ_ONLY;
import static java.sql.ResultSet.CONCUR_UPDATABLE;
import static java.sql.ResultSet.TYPE_FORWARD_ONLY;
import static java.sql.ResultSet.TYPE_SCROLL_INSENSITIVE;
import static java.sql.ResultSet.TYPE_SCROLL_SENSITIVE;
import static org.hibernate.cfg.AvailableSettings.JAKARTA_JDBC_URL;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.hibernate.internal.cfg.MongoConfigurationBuilder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoConnectionTests {

    @Mock
    private ClientSession clientSession;

    @Mock
    private MongoClient mongoClient;

    private MongoConnection mongoConnection;

    @BeforeEach
    void beforeEach() {
        mongoConnection = new MongoConnection(
                new MongoConfigurationBuilder(Map.of(JAKARTA_JDBC_URL, "mongodb://host/db")).build(),
                mongoClient,
                clientSession);
    }

    @Nested
    class CloseTests {
        @Test
        void testNoopWhenCloseAgain() throws SQLException {

            mongoConnection.close();
            assertTrue(mongoConnection.isClosed());

            verify(clientSession).close();

            mongoConnection.close();

            verifyNoMoreInteractions(clientSession);
        }

        @Test
        void testClosedWhenSessionClosingThrowsException() {

            doThrow(new RuntimeException()).when(clientSession).close();

            assertThrows(SQLException.class, () -> mongoConnection.close());

            assertTrue(mongoConnection.isClosed());
        }
    }

    @Test
    void testCheckClosed() throws SQLException {
        mongoConnection.close();
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
                () -> assertThrowsClosedException(() -> mongoConnection.setAutoCommit(false)),
                () -> assertThrowsClosedException(mongoConnection::getAutoCommit),
                () -> assertThrowsClosedException(mongoConnection::rollback),
                () -> assertThrowsClosedException(mongoConnection::createStatement),
                () -> assertThrowsClosedException(() -> mongoConnection.prepareStatement(exampleUpdateMql)),
                () -> assertThrowsClosedException(
                        () -> mongoConnection.prepareStatement(exampleQueryMql, TYPE_FORWARD_ONLY, CONCUR_READ_ONLY)),
                () -> assertThrowsClosedException(
                        () -> mongoConnection.createArrayOf("myArrayType", new String[] {"value1", "value2"})),
                () -> assertThrowsClosedException(
                        () -> mongoConnection.createStruct("myStructType", new Object[] {1, "Toronto"})),
                () -> assertThrowsClosedException(mongoConnection::getMetaData),
                () -> assertThrowsClosedException(mongoConnection::getCatalog),
                () -> assertThrowsClosedException(mongoConnection::getSchema),
                () -> assertThrowsClosedException(mongoConnection::getWarnings),
                () -> assertThrowsClosedException(mongoConnection::clearWarnings),
                () -> assertThrowsClosedException(() -> mongoConnection.isWrapperFor(Connection.class)));
    }

    private void assertThrowsClosedException(Executable executable) {
        var exception = assertThrows(SQLException.class, executable);
        assertEquals("MongoConnection has been closed", exception.getMessage());
    }

    @Nested
    class TransactionTests {

        @Nested
        class SetAutoCommitTests {

            @ParameterizedTest(name = "No-op when autoCommit state ({0}) not changed")
            @ValueSource(booleans = {true, false})
            void testNoOpWhenAutoCommitNotChanged(boolean autoCommit) throws Exception {

                if (!autoCommit) {
                    mongoConnection.setAutoCommit(false);
                    verify(clientSession).hasActiveTransaction();
                } else {
                    assertTrue(mongoConnection.getAutoCommit());
                }

                mongoConnection.setAutoCommit(autoCommit);

                verifyNoMoreInteractions(clientSession);
            }

            @Nested
            class AutoCommitStateChangedTests {

                @ParameterizedTest(
                        name =
                                "AutoCommit state changed (false -> true): commit existing transaction (successful: {0})")
                @ValueSource(booleans = {true, false})
                void testTryingToCommitExistingTransactionWhenAutoCommitChangedToTrue(boolean successful)
                        throws SQLException {

                    mongoConnection.setAutoCommit(false);
                    doReturn(true).when(clientSession).hasActiveTransaction();
                    if (!successful) {
                        doThrow(RuntimeException.class).when(clientSession).commitTransaction();
                    }

                    if (successful) {
                        mongoConnection.setAutoCommit(true);
                    } else {
                        assertThrows(SQLException.class, () -> mongoConnection.setAutoCommit(true));
                    }
                    verify(clientSession).commitTransaction();
                }

                @Test
                @DisplayName("No-op when no active transaction exists and autoCommit state changed (false -> true)")
                void testNoopWhenNoExistingTransactionAndAutoCommitChangedToTrue() throws SQLException {

                    mongoConnection.setAutoCommit(false);
                    doReturn(false).when(clientSession).hasActiveTransaction();

                    mongoConnection.setAutoCommit(true);

                    verify(clientSession, atLeast(1)).hasActiveTransaction();
                    verifyNoMoreInteractions(clientSession);
                }

                @Test
                @DisplayName("No transaction is started when autoCommit state changed (true -> false)")
                void testNoTransactionStartedWhenAutoCommitChangedToFalse() throws SQLException {

                    mongoConnection.setAutoCommit(false);

                    verify(clientSession, never()).startTransaction();
                }
            }
        }

        @Nested
        class CommitTests {

            @Test
            @DisplayName("No-op when no active transaction exists during transaction commit")
            void testNoopWhenNoTransactionExistsAndCommit() throws SQLException {

                doReturn(false).when(clientSession).hasActiveTransaction();
                mongoConnection.setAutoCommit(false);

                assertDoesNotThrow(() -> mongoConnection.commit());
                verifyNoMoreInteractions(clientSession);
            }

            @Test
            @DisplayName("SQLException is thrown when autoCommit state is true during transaction commit")
            void testSQLExceptionThrownWhenAutoCommitIsTrue() throws SQLException {

                assertTrue(mongoConnection.getAutoCommit());

                assertThrows(SQLException.class, () -> mongoConnection.commit());
            }

            @Test
            @DisplayName("SQLException is thrown when transaction commit failed")
            void testSQLExceptionThrownWhenTransactionCommitFailed() throws SQLException {

                mongoConnection.setAutoCommit(false);
                doReturn(true).when(clientSession).hasActiveTransaction();
                doThrow(RuntimeException.class).when(clientSession).commitTransaction();

                assertThrows(SQLException.class, () -> mongoConnection.commit());
            }
        }

        @Nested
        class RollbackTests {

            @Test
            @DisplayName("No-op when no active transaction exists during transaction rollback")
            void testNoopWhenNoTransactionExistsAndRollback() throws SQLException {

                doReturn(false).when(clientSession).hasActiveTransaction();
                mongoConnection.setAutoCommit(false);

                assertDoesNotThrow(() -> mongoConnection.rollback());
                verifyNoMoreInteractions(clientSession);
            }

            @Test
            @DisplayName("SQLException is thrown when autoCommit state is true during transaction rollback")
            void testSQLExceptionThrownWhenAutoCommitIsTrue() throws SQLException {

                assertTrue(mongoConnection.getAutoCommit());

                assertThrows(SQLException.class, () -> mongoConnection.rollback());
            }

            @Test
            @DisplayName("SQLException is thrown when transaction rollback failed")
            void testSQLExceptionThrownWhenTransactionRollbackFailed() throws SQLException {

                mongoConnection.setAutoCommit(false);
                doReturn(true).when(clientSession).hasActiveTransaction();
                doThrow(RuntimeException.class).when(clientSession).abortTransaction();

                assertThrows(SQLException.class, () -> mongoConnection.rollback());
            }
        }
    }

    @Nested
    class GetMetaDataTests {

        @Mock
        private MongoDatabase mongoDatabase;

        @Test
        @DisplayName("Happy path for MongoDatabaseMetaData fetching")
        void testSuccess() {

            doReturn(mongoDatabase).when(mongoClient).getDatabase(eq("admin"));
            var commandResultJson =
                    """
                    {
                        "ok": 1.0,
                        "version": "8.0.1",
                        "versionArray": [8, 0, 1, 0]
                    }""";
            var commandResultDoc = Document.parse(commandResultJson);
            doReturn(commandResultDoc)
                    .when(mongoDatabase)
                    .runCommand(any(ClientSession.class), argThat(arg -> "buildinfo"
                            .equals(arg.toBsonDocument().getFirstKey())));

            var metaData = assertDoesNotThrow(() -> mongoConnection.getMetaData());

            assertAll(
                    () -> assertEquals(MONGO_DATABASE_PRODUCT_NAME, metaData.getDatabaseProductName()),
                    () -> assertEquals(MONGO_JDBC_DRIVER_NAME, metaData.getDriverName()),
                    () -> assertEquals("8.0.1", metaData.getDatabaseProductVersion()),
                    () -> assertEquals(8, metaData.getDatabaseMajorVersion()),
                    () -> assertEquals(0, metaData.getDatabaseMinorVersion()));
        }

        @Test
        @DisplayName("SQLException is thrown when MongoConnection#getMetaData() failed while interacting with db")
        void testSQLExceptionThrownWhenMetaDataFetchingFailed() {
            doReturn(mongoDatabase).when(mongoClient).getDatabase(eq("admin"));
            doThrow(new RuntimeException())
                    .when(mongoDatabase)
                    .runCommand(any(ClientSession.class), argThat(arg -> "buildinfo"
                            .equals(arg.toBsonDocument().getFirstKey())));
            assertThrows(SQLException.class, () -> mongoConnection.getMetaData());
        }
    }

    @Nested
    class ResultSetSupportTests {

        private static final String EXAMPLE_MQL = "{}";

        @ParameterizedTest(name = "ResultSet type: {0}")
        @ValueSource(ints = {TYPE_FORWARD_ONLY, TYPE_SCROLL_SENSITIVE, TYPE_SCROLL_INSENSITIVE})
        void testType(int resultSetType) {
            Executable executable = () -> mongoConnection
                    .prepareStatement(EXAMPLE_MQL, resultSetType, CONCUR_READ_ONLY)
                    .close();
            if (resultSetType == TYPE_FORWARD_ONLY) {
                assertDoesNotThrow(executable);
            } else {
                assertThrows(SQLException.class, executable);
            }
        }

        @ParameterizedTest(name = "ResultSet concurrency: {0}")
        @ValueSource(ints = {CONCUR_READ_ONLY, CONCUR_UPDATABLE})
        void testConcurrency(int resultSetConcurrency) {
            Executable executable = () -> mongoConnection
                    .prepareStatement(EXAMPLE_MQL, TYPE_FORWARD_ONLY, resultSetConcurrency)
                    .close();
            if (resultSetConcurrency == CONCUR_READ_ONLY) {
                assertDoesNotThrow(executable);
            } else {
                assertThrows(SQLException.class, executable);
            }
        }
    }
}

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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoConnectionTests {

    @Mock
    private ClientSession clientSession;

    @Mock
    private MongoClient mongoClient;

    @InjectMocks
    private MongoConnection mongoConnection;

    @Nested
    class CloseTests {
        @Test
        void testNoopWhenCloseAgain() throws SQLException {
            // given
            mongoConnection.close();
            assertTrue(mongoConnection.isClosed());

            verify(clientSession).close();

            // when
            mongoConnection.close();

            // then
            verifyNoMoreInteractions(clientSession);
        }

        @Test
        void testClosedWhenSessionClosingThrowsException() {
            // given
            doThrow(new RuntimeException()).when(clientSession).close();

            // when
            assertThrows(SQLException.class, () -> mongoConnection.close());

            // then
            assertTrue(mongoConnection.isClosed());
        }
    }

    @Nested
    class ClosedTests {

        interface ConnectionMethodInvocation {
            void runOn(MongoConnection conn) throws SQLException;
        }

        @ParameterizedTest(name = "SQLException is thrown when \"{0}\" is called on a closed MongoConnection")
        @MethodSource("getMongoConnectionMethodInvocationsImpactedByClosing")
        void testCheckClosed(String label, ConnectionMethodInvocation methodInvocation) throws SQLException {
            // given
            mongoConnection.close();

            // when && then
            var exception = assertThrows(SQLException.class, () -> methodInvocation.runOn(mongoConnection));
            assertEquals("MongoConnection has been closed", exception.getMessage());
        }

        private static Stream<Arguments> getMongoConnectionMethodInvocationsImpactedByClosing() {
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
            return Map.<String, ConnectionMethodInvocation>ofEntries(
                            Map.entry("setAutoCommit(boolean)", conn -> conn.setAutoCommit(false)),
                            Map.entry("getAutoCommit()", MongoConnection::getAutoCommit),
                            Map.entry("commit()", MongoConnection::commit),
                            Map.entry("rollback()", MongoConnection::rollback),
                            Map.entry("createStatement()", MongoConnection::createStatement),
                            Map.entry("prepareStatement(String)", conn -> conn.prepareStatement(exampleUpdateMql)),
                            Map.entry(
                                    "prepareStatement(String,int,int)",
                                    conn -> conn.prepareStatement(
                                            exampleQueryMql, TYPE_FORWARD_ONLY, CONCUR_READ_ONLY)),
                            Map.entry(
                                    "createArrayOf(String,Object[])",
                                    conn -> conn.createArrayOf("myArrayType", new String[] {"value1", "value2"})),
                            Map.entry(
                                    "createStruct(String,Object[])",
                                    conn -> conn.createStruct("myStructType", new Object[] {1, "Toronto"})),
                            Map.entry("getMetaData()", MongoConnection::getMetaData),
                            Map.entry("getCatalog()", MongoConnection::getCatalog),
                            Map.entry("getSchema()", MongoConnection::getSchema),
                            Map.entry("getWarnings()", MongoConnection::getWarnings),
                            Map.entry("clearWarnings()", MongoConnection::clearWarnings),
                            Map.entry("isWrapperFor()", conn -> conn.isWrapperFor(Connection.class)))
                    .entrySet()
                    .stream()
                    .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
        }
    }

    @Nested
    class TransactionTests {

        @Nested
        class SetAutoCommitTests {

            @ParameterizedTest(name = "No-op when autoCommit state ({0}) not changed")
            @ValueSource(booleans = {true, false})
            void testNoOpWhenAutoCommitNotChanged(boolean autoCommit) throws Exception {
                // given
                if (!autoCommit) {
                    mongoConnection.setAutoCommit(false);
                    verify(clientSession).hasActiveTransaction();
                } else {
                    assertTrue(mongoConnection.getAutoCommit());
                }

                // when
                mongoConnection.setAutoCommit(autoCommit);

                // then
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
                    // given
                    mongoConnection.setAutoCommit(false);
                    doReturn(true).when(clientSession).hasActiveTransaction();
                    if (!successful) {
                        doThrow(RuntimeException.class).when(clientSession).commitTransaction();
                    }

                    // when && then
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
                    // given
                    mongoConnection.setAutoCommit(false);
                    doReturn(false).when(clientSession).hasActiveTransaction();

                    // when
                    mongoConnection.setAutoCommit(true);

                    // then
                    verify(clientSession, atLeast(1)).hasActiveTransaction();
                    verifyNoMoreInteractions(clientSession);
                }

                @Test
                @DisplayName("No transaction is started when autoCommit state changed (true -> false)")
                void testNoTransactionStartedWhenAutoCommitChangedToFalse() throws SQLException {
                    // given

                    // when
                    mongoConnection.setAutoCommit(false);

                    // then
                    verify(clientSession, never()).startTransaction();
                }
            }
        }

        @Nested
        class CommitTests {

            @Test
            @DisplayName("No-op when no active transaction exists during transaction commit")
            void testNoopWhenNoTransactionExistsAndCommit() throws SQLException {
                // given
                doReturn(false).when(clientSession).hasActiveTransaction();
                mongoConnection.setAutoCommit(false);

                // when && then
                assertDoesNotThrow(() -> mongoConnection.commit());
                verifyNoMoreInteractions(clientSession);
            }

            @Test
            @DisplayName("SQLException is thrown when autoCommit state is true during transaction commit")
            void testSQLExceptionThrownWhenAutoCommitIsTrue() throws SQLException {
                // given
                assertTrue(mongoConnection.getAutoCommit());

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.commit());
            }

            @Test
            @DisplayName("SQLException is thrown when transaction commit failed")
            void testSQLExceptionThrownWhenTransactionCommitFailed() throws SQLException {
                // given
                mongoConnection.setAutoCommit(false);
                doReturn(true).when(clientSession).hasActiveTransaction();
                doThrow(RuntimeException.class).when(clientSession).commitTransaction();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.commit());
            }
        }

        @Nested
        class RollbackTests {

            @Test
            @DisplayName("No-op when no active transaction exists during transaction rollback")
            void testNoopWhenNoTransactionExistsAndRollback() throws SQLException {
                // given
                doReturn(false).when(clientSession).hasActiveTransaction();
                mongoConnection.setAutoCommit(false);

                // when && then
                assertDoesNotThrow(() -> mongoConnection.rollback());
                verifyNoMoreInteractions(clientSession);
            }

            @Test
            @DisplayName("SQLException is thrown when autoCommit state is true during transaction rollback")
            void testSQLExceptionThrownWhenAutoCommitIsTrue() throws SQLException {
                // given
                assertTrue(mongoConnection.getAutoCommit());

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.rollback());
            }

            @Test
            @DisplayName("SQLException is thrown when transaction rollback failed")
            void testSQLExceptionThrownWhenTransactionRollbackFailed() throws SQLException {
                // given
                mongoConnection.setAutoCommit(false);
                doReturn(true).when(clientSession).hasActiveTransaction();
                doThrow(RuntimeException.class).when(clientSession).abortTransaction();

                // when && then
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
            // given
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

            // when
            var metaData = assertDoesNotThrow(() -> mongoConnection.getMetaData());

            // then
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
            // given
            doReturn(mongoDatabase).when(mongoClient).getDatabase(eq("admin"));
            doThrow(new RuntimeException())
                    .when(mongoDatabase)
                    .runCommand(any(ClientSession.class), argThat(arg -> "buildinfo"
                            .equals(arg.toBsonDocument().getFirstKey())));
            // when && then
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

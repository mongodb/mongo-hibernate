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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.mongodb.client.ClientSession;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoConnectionTests {

    @Mock(answer = RETURNS_SMART_NULLS)
    private ClientSession clientSession;

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
    class TransactionTests {

        @Nested
        class SetAutoCommitTests {

            @ParameterizedTest(name = "No-op when autoCommit state ({0}) not changed")
            @ValueSource(booleans = {true, false})
            void testNoOpWhenAutoCommitNotChanged(boolean autoCommit) throws Exception {
                // given
                if (!autoCommit) {
                    mongoConnection.setAutoCommit(false);
                    verify(clientSession).startTransaction();
                    verify(clientSession).hasActiveTransaction();
                } else {
                    assertTrue(mongoConnection.getAutoCommit());
                }

                assertEquals(mongoConnection.getAutoCommit(), autoCommit);

                // when
                mongoConnection.setAutoCommit(autoCommit);

                // then
                verifyNoMoreInteractions(clientSession);
            }

            @Nested
            class AutoCommitStateChangedTests {

                @ParameterizedTest(name = "AutoCommit (true -> false): start transaction (successful: {0})")
                @ValueSource(booleans = {true, false})
                void testTryingToStartTransactionWhenAutoCommitChangedToFalse(boolean successful) throws SQLException {
                    // given
                    assertTrue(mongoConnection.getAutoCommit());
                    if (!successful) {
                        doThrow(RuntimeException.class).when(clientSession).startTransaction();
                    }

                    // when && then
                    if (successful) {
                        mongoConnection.setAutoCommit(false);
                    } else {
                        assertThrows(SQLException.class, () -> mongoConnection.setAutoCommit(false));
                    }
                    verify(clientSession).startTransaction();
                }

                @ParameterizedTest(
                        name =
                                "AutoCommit state changed (false -> true): commit existing transaction (successful: {0})")
                @ValueSource(booleans = {true, false})
                void testTryingToCommitExistingTransactionWhenAutoCommitChangedToTrue(boolean successful)
                        throws SQLException {
                    // given
                    mongoConnection.setAutoCommit(false);
                    verify(clientSession).startTransaction();

                    assertFalse(mongoConnection.getAutoCommit());
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
                    verify(clientSession).startTransaction();
                    mongoConnection.commit();
                    verify(clientSession).commitTransaction();

                    assertFalse(mongoConnection.getAutoCommit());

                    // when
                    mongoConnection.setAutoCommit(true);

                    // then
                    assertTrue(mongoConnection.getAutoCommit());
                    verify(clientSession, atLeast(1)).hasActiveTransaction();
                    verifyNoMoreInteractions(clientSession);
                }
            }

            @ParameterizedTest(
                    name = "SQLException is thrown when 'setAutoCommit({0})' is called on a closed MongoConnection")
            @ValueSource(booleans = {true, false})
            void testSQLExceptionThrowWhenCalledOnClosedConnection(boolean autoCommit) throws SQLException {
                // given
                mongoConnection.close();
                verify(clientSession).close();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.setAutoCommit(autoCommit));
                verifyNoMoreInteractions(clientSession);
            }
        }

        @Nested
        class CommitTests {

            @Test
            @DisplayName("AutoCommit state should be false when committing transaction")
            void testSQLExceptionThrownWhenAutoCommitIsTrue() throws SQLException {
                assertTrue(mongoConnection.getAutoCommit());
                assertThrows(SQLException.class, () -> mongoConnection.commit());
            }

            @Test
            @DisplayName("SQLException is thrown when transaction commit failed")
            void testSQLExceptionThrownWhenTransactionCommitFailed() throws SQLException {
                // given
                mongoConnection.setAutoCommit(false);
                verify(clientSession).startTransaction();

                doThrow(RuntimeException.class).when(clientSession).commitTransaction();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.commit());
            }

            @Test
            @DisplayName("SQLException is thrown when 'commit()' is called on a closed MongoConnection")
            void testSQLExceptionThrowWhenCalledOnClosedConnection() throws SQLException {
                // given
                mongoConnection.close();
                verify(clientSession).close();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.commit());
                verifyNoMoreInteractions(clientSession);
            }
        }

        @Nested
        class RollbackTests {

            @Test
            @DisplayName("AutoCommit state should be false when rolling back transaction")
            void testSQLExceptionThrownWhenAutoCommitIsTrue() throws SQLException {
                assertTrue(mongoConnection.getAutoCommit());
                assertThrows(SQLException.class, () -> mongoConnection.rollback());
            }

            @Test
            @DisplayName("SQLException is thrown when transaction rollback failed")
            void testSQLExceptionThrownWhenTransactionRollbackFailed() throws SQLException {
                // given
                mongoConnection.setAutoCommit(false);
                verify(clientSession).startTransaction();

                doThrow(RuntimeException.class).when(clientSession).abortTransaction();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.rollback());
            }

            @Test
            @DisplayName("SQLException is thrown when 'rollback()' is called on a closed MongoConnection")
            void testSQLExceptionThrowWhenCalledOnClosedConnection() throws SQLException {
                // given
                mongoConnection.close();
                verify(clientSession).close();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.rollback());
                verifyNoMoreInteractions(clientSession);
            }
        }

        @Nested
        class TransactionIsolationLevelTests {

            @ParameterizedTest
            @ValueSource(
                    ints = {
                        Connection.TRANSACTION_NONE,
                        Connection.TRANSACTION_READ_UNCOMMITTED,
                        Connection.TRANSACTION_READ_COMMITTED,
                        Connection.TRANSACTION_REPEATABLE_READ,
                        Connection.TRANSACTION_SERIALIZABLE
                    })
            @DisplayName("MongoDB Dialect doesn't support JDBC transaction isolation level setting")
            void testSetUnsupported(int level) {
                // when && then
                assertThrows(
                        SQLFeatureNotSupportedException.class, () -> mongoConnection.setTransactionIsolation(level));
                verifyNoInteractions(clientSession);
            }

            @ParameterizedTest
            @ValueSource(
                    ints = {
                        Connection.TRANSACTION_NONE,
                        Connection.TRANSACTION_READ_UNCOMMITTED,
                        Connection.TRANSACTION_READ_COMMITTED,
                        Connection.TRANSACTION_REPEATABLE_READ,
                        Connection.TRANSACTION_SERIALIZABLE
                    })
            @DisplayName(
                    "SQLException is thrown when 'setTransactionIsolation({0})' is called on a closed MongoConnection")
            void testSQLExceptionThrowWhenCalledOnClosedConnection(int level) throws SQLException {
                // given
                mongoConnection.close();
                verify(clientSession).close();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.setTransactionIsolation(level));
                verifyNoMoreInteractions(clientSession);
            }

            @Test
            @DisplayName("MongoDB Dialect doesn't support JDBC transaction isolation level fetching")
            void testGetUnsupported() {
                // when && then
                assertThrows(SQLFeatureNotSupportedException.class, () -> mongoConnection.getTransactionIsolation());
                verifyNoInteractions(clientSession);
            }

            @Test
            @DisplayName(
                    "SQLException is thrown when 'getTransactionIsolation()' is called on a closed MongoConnection")
            void testSQLExceptionThrowWhenCalledOnClosedConnection() throws SQLException {
                // given
                mongoConnection.close();
                verify(clientSession).close();

                // when && then
                assertThrows(SQLException.class, () -> mongoConnection.getTransactionIsolation());
                verifyNoMoreInteractions(clientSession);
            }
        }
    }
}

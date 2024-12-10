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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import java.sql.SQLException;
import org.bson.Document;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@NullUnmarked
@ExtendWith(MockitoExtension.class)
class MongoConnectionTests {

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ClientSession clientSession;

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private MongoClient mongoClient;

    @InjectMocks
    private MongoConnection mongoConnection;

    @Test
    @DisplayName("no-op when a closed MongoConnection is closed again")
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
    @DisplayName("MongoConnection#isClosed() returns true if the previous closing ended up with SQLException")
    void testClosedWhenSessionClosingThrowsException() {

        // given
        doThrow(new RuntimeException()).when(clientSession).close();

        // when
        assertThrows(SQLException.class, () -> mongoConnection.close());

        // then
        assertTrue(mongoConnection.isClosed());
    }

    @Nested
    class GetMetaDataTests {

        @Mock(answer = Answers.RETURNS_SMART_NULLS)
        private MongoDatabase mongoDatabase;

        @BeforeEach
        void setUp() {
            doReturn(mongoDatabase).when(mongoClient).getDatabase(eq("admin"));
        }

        @Test
        @DisplayName("Happy path for MongoDatabaseMetaData fetching")
        void testSuccess() {
            // given
            var commandResultJson =
                    """
                    {"ok": 1.0, "version": "8.0.1", "versionArray": [8, 0, 1, 0]}
                    """;
            var commandResultDoc = Document.parse(commandResultJson);
            doReturn(commandResultDoc).when(mongoDatabase).runCommand(argThat(arg -> "buildinfo"
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
            doThrow(new RuntimeException()).when(mongoDatabase).runCommand(argThat(arg -> "buildinfo"
                    .equals(arg.toBsonDocument().getFirstKey())));
            // when && then
            assertThrows(SQLException.class, () -> mongoConnection.getMetaData());
        }

        @Test
        @DisplayName("SQLException is thrown when MongoConnection#getMetaData() is called on a closed connection")
        void testSQLExceptionThrownWhenConnectionClosed() throws SQLException {
            // given
            mongoConnection.close();

            // when && then
            assertThrows(SQLException.class, () -> mongoConnection.getMetaData());
        }
    }
}

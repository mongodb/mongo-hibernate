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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.mongodb.client.ClientSession;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MongoConnectionTests {

    @Mock(answer = Answers.RETURNS_SMART_NULLS)
    private ClientSession clientSession;

    @InjectMocks
    private MongoConnection mongoConnection;

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

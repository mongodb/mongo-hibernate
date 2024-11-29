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

package com.mongodb.hibernate.translate.attachment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SQLAstVisitorStateManagerTests {

    private SQLAstVisitorStateManager sqlAstVisitorStateManager;

    @BeforeEach
    void setUp() {
        sqlAstVisitorStateManager = new SQLAstVisitorStateManager();
    }

    @Test
    void testSuccess() {
        // given
        AttachmentKey<String> attachmentKey = AttachmentKey.COLLECTION_NAME;
        Runnable runnable = () -> sqlAstVisitorStateManager.attach(attachmentKey, "books");

        // when
        String returnValue = sqlAstVisitorStateManager.detach(attachmentKey, runnable);

        // then
        assertEquals("books", returnValue);
        assertTrue(sqlAstVisitorStateManager.state.isEmpty());
    }

    @Test
    void testReturnTypesMismatch() {
        // given
        Runnable runnable = () -> sqlAstVisitorStateManager.attach(AttachmentKey.COLLECTION_NAME, "books");

        // when && then
        assertThrows(
                IllegalArgumentException.class,
                () -> sqlAstVisitorStateManager.detach(AttachmentKey.COLUMN_NAME, runnable));
        assertTrue(sqlAstVisitorStateManager.state.isEmpty());
    }

    @Test
    void testRunnableThrowException() {
        // given
        Runnable runnable = () -> {
            throw new NullPointerException();
        };

        // when && then
        assertThrows(
                NullPointerException.class,
                () -> sqlAstVisitorStateManager.detach(AttachmentKey.COLUMN_NAME, runnable));
        assertTrue(sqlAstVisitorStateManager.state.isEmpty());
    }

    @Test
    void testRunnableNeverSatisfiedExpectation() {
        // given
        Runnable runnable = () -> {};

        // when && then
        assertThrows(
                IllegalArgumentException.class,
                () -> sqlAstVisitorStateManager.detach(AttachmentKey.COLLECTION_NAME, runnable));
        assertTrue(sqlAstVisitorStateManager.state.isEmpty());
    }
}

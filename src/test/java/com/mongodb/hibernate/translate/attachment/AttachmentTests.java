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

class AttachmentTests {

    private Attachment attachment;

    @BeforeEach
    void setUp() {
        attachment = new Attachment();
    }

    @Test
    void testSuccess() {
        // given
        AttachmentKey<String> attachmentKey = AttachmentKey.COLLECTION_NAME;
        Runnable runnable = () -> attachment.attach(attachmentKey, "books");

        // when
        String returnValue = attachment.expect(attachmentKey, runnable);

        // then
        assertEquals("books", returnValue);
        assertTrue(attachment.isBlank());
    }

    @Test
    void testEmbeddedAttachments() {
        // given
        Runnable collectionAttacher = () -> {
            attachment.expect(AttachmentKey.COLUMN_NAME, () -> attachment.attach(AttachmentKey.COLUMN_NAME, "title"));
            attachment.attach(AttachmentKey.COLLECTION_NAME, "books");
        };

        // when
        String returnValue = attachment.expect(AttachmentKey.COLLECTION_NAME, collectionAttacher);

        // then
        assertEquals("books", returnValue);
        assertTrue(attachment.isBlank());
    }

    @Test
    void testReturnTypesMismatch() {
        // given
        Runnable runnable = () -> attachment.attach(AttachmentKey.COLLECTION_NAME, "books");

        // when && then
        assertThrows(IllegalArgumentException.class, () -> attachment.expect(AttachmentKey.COLUMN_NAME, runnable));
        assertTrue(attachment.isBlank());
    }

    @Test
    void testRunnableThrowException() {
        // given
        Runnable runnable = () -> {
            throw new NullPointerException();
        };

        // when && then
        assertThrows(NullPointerException.class, () -> attachment.expect(AttachmentKey.COLUMN_NAME, runnable));
        assertTrue(attachment.isBlank());
    }

    @Test
    void testRunnableNeverSatisfiedExpectation() {
        // given
        Runnable runnable = () -> {};

        // when && then
        assertThrows(IllegalArgumentException.class, () -> attachment.expect(AttachmentKey.COLLECTION_NAME, runnable));
        assertTrue(attachment.isBlank());
    }

    @Test
    void testAttachmentDuplicated() {
        // given
        Runnable runnable = () -> {
            attachment.attach(AttachmentKey.COLLECTION_NAME, "books1");
            attachment.attach(AttachmentKey.COLLECTION_NAME, "books2");
        };

        // when && then
        assertThrows(IllegalStateException.class, () -> attachment.expect(AttachmentKey.COLLECTION_NAME, runnable));
        assertTrue(attachment.isBlank());
    }
}

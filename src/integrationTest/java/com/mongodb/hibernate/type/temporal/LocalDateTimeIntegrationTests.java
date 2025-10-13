/*
 * Copyright 2025-present MongoDB, Inc.
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

package com.mongodb.hibernate.type.temporal;

import static com.mongodb.hibernate.type.temporal.CalendarIntegrationTests.assertNotSupported;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LocalDateTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithBasicPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithArrayPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithBasicPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithArrayPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithCollectionPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithBasicPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithArrayPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithCollectionPersistentAttribute<
                                LocalDateTime>() {}.getClass()));
    }
}

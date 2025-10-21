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
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.*;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class LocalDateTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<LocalDateTime>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithBasicPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithArrayPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<LocalDateTime>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<LocalDateTime>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithCollectionOfAggregateEmbeddableWithBasicPersistentAttribute<
                                LocalDateTime>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                LocalDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedCollectionOfAggregateEmbeddablePersistentAttribute<
                                LocalDateTime>() {}.getClass()));
    }
}

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

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class OffsetDateTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<OffsetDateTime>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithBasicPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithArrayPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<OffsetDateTime>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<OffsetDateTime>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithCollectionOfAggregateEmbeddableWithBasicPersistentAttribute<
                                OffsetDateTime>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                OffsetDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedCollectionOfAggregateEmbeddablePersistentAttribute<
                                OffsetDateTime>() {}.getClass()));
    }
}

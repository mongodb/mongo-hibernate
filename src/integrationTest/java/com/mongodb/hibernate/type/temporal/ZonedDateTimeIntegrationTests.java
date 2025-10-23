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

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ZonedDateTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<ZonedDateTime>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithBasicPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithArrayPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<ZonedDateTime>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<ZonedDateTime>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionOfAggregateEmbeddable<ZonedDateTime>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedCollectionOfAggregateEmbeddable<ZonedDateTime>() {}.getClass()));
    }
}

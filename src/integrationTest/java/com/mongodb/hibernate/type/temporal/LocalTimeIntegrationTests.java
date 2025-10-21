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

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class LocalTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<LocalTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<LocalTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<LocalTime>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(new ItemWithEmbeddableWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<LocalTime>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<LocalTime>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithCollectionOfAggregateEmbeddableWithBasicPersistentAttribute<
                                LocalTime>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedCollectionOfAggregateEmbeddablePersistentAttribute<
                                LocalTime>() {}.getClass()));
    }
}

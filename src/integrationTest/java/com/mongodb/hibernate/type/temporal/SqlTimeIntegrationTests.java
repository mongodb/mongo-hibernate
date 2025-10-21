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

import java.sql.Time;
import org.junit.jupiter.api.Test;

class SqlTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<Time>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<Time>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<Time>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(new ItemWithEmbeddableWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithCollectionPersistentAttribute<Time>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<Time>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithCollectionOfAggregateEmbeddableWithBasicPersistentAttribute<Time>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedCollectionOfAggregateEmbeddablePersistentAttribute<Time>() {}.getClass()));
    }
}

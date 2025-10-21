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

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

class SqlTimestampIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<Timestamp>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(new ItemWithEmbeddableWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<Timestamp>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<Timestamp>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionOfAggregateEmbeddable<Timestamp>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new ItemWithNestedCollectionOfAggregateEmbeddable<Timestamp>() {}.getClass()));
    }
}

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

import java.time.OffsetTime;
import org.junit.jupiter.api.Test;

class OffsetTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<OffsetTime>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithEmbeddableWithCollectionPersistentAttribute<OffsetTime>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<OffsetTime>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionOfAggregateEmbeddable<OffsetTime>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedCollectionOfAggregateEmbeddable<OffsetTime>() {}.getClass()));
    }
}

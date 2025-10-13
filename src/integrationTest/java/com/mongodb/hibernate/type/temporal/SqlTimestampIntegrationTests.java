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

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

class SqlTimestampIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<Timestamp>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithFlattenedEmbeddableId<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithBasicPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithArrayPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                Timestamp>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                Timestamp>() {}.getClass()));
    }
}

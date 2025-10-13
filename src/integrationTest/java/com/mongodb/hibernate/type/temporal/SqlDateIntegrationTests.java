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

import java.sql.Date;
import org.junit.jupiter.api.Test;

class SqlDateIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<Date>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithFlattenedEmbeddableId<Date>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithBasicPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithArrayPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                Date>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                Date>() {}.getClass()));
    }
}

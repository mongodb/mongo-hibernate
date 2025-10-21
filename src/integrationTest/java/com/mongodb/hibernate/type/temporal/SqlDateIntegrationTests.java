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

import java.sql.Date;
import org.junit.jupiter.api.Test;

class SqlDateIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new ItemWithId<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithFlattenedEmbeddableId<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionPersistentAttribute<Date>() {}.getClass()),

                // Flattened Embeddable
                () -> assertNotSupported(new ItemWithEmbeddableWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithEmbeddableWithCollectionPersistentAttribute<Date>() {}.getClass()),

                // Nested flattened embeddable
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedEmbeddableWithCollectionPersistentAttribute<Date>() {}.getClass()),

                // Aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithCollectionOfAggregateEmbeddable<Date>() {}.getClass()),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(
                        new ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<Date>() {}.getClass()),
                () -> assertNotSupported(new ItemWithNestedCollectionOfAggregateEmbeddable<Date>() {}.getClass()));
    }
}

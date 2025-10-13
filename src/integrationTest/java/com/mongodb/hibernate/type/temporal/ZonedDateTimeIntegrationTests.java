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

import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class ZonedDateTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithFlattenedEmbeddableId<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithBasicPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithArrayPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithBasicPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithArrayPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                ZonedDateTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                ZonedDateTime>() {}.getClass()));
    }
}

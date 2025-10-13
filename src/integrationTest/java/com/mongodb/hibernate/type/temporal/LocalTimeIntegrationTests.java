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

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class LocalTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<LocalTime>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithFlattenedEmbeddableId<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithBasicPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithArrayPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithAggregateEmbeddableWithCollectionPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithBasicPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithArrayPersistentAttribute<
                                LocalTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedAggregateEmbeddableWithCollectionPersistentAttribute<
                                LocalTime>() {}.getClass()));
    }
}

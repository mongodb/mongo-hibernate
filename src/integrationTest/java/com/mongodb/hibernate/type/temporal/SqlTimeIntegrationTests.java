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

import java.sql.Time;
import org.junit.jupiter.api.Test;

class SqlTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<Time>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(new UnsupportedItems.ItemWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithCollectionPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithBasicPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithArrayPersistentAttribute<Time>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithCollectionPersistentAttribute<
                                Time>() {}.getClass()));
    }
}

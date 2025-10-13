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

import java.time.OffsetTime;
import org.junit.jupiter.api.Test;

class OffsetTimeIntegrationTests {
    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(new UnsupportedItems.ItemWithId<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithCollectionPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithBasicPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithArrayPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithEmbeddableWithCollectionPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithBasicPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithArrayPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedEmbeddableWithCollectionPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithBasicPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithArrayPersistentAttribute<OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithStructWithCollectionPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithBasicPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithArrayPersistentAttribute<
                                OffsetTime>() {}.getClass()),
                () -> assertNotSupported(
                        new UnsupportedItems.ItemWithNestedStructWithCollectionPersistentAttribute<
                                OffsetTime>() {}.getClass()));
    }
}

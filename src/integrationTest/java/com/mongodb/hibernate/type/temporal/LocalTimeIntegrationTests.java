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
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.LocalTimeItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LocalTimeIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(LocalTimeItems.WithId.class),
                () -> assertNotSupported(LocalTimeItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(LocalTimeItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(LocalTimeItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(LocalTimeItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(LocalTimeItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        LocalTimeItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(
                        LocalTimeItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        LocalTimeItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(LocalTimeItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}

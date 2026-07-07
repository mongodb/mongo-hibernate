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

import static com.mongodb.hibernate.type.UnsupportedTypeAssertions.assertNotSupported;
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.LocalDateTimeItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LocalDateTimeIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(LocalDateTimeItems.WithId.class),
                () -> assertNotSupported(LocalDateTimeItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(LocalDateTimeItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(LocalDateTimeItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(LocalDateTimeItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        LocalDateTimeItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(LocalDateTimeItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        LocalDateTimeItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        LocalDateTimeItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(
                        LocalDateTimeItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        LocalDateTimeItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(LocalDateTimeItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}

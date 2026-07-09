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
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.OffsetTimeItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class OffsetTimeIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(OffsetTimeItems.WithId.class),
                () -> assertNotSupported(OffsetTimeItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(OffsetTimeItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(OffsetTimeItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(OffsetTimeItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(OffsetTimeItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        OffsetTimeItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        OffsetTimeItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(
                        OffsetTimeItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        OffsetTimeItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(OffsetTimeItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}

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
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.ZonedDateTimeItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class ZonedDateTimeIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(ZonedDateTimeItems.WithId.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(ZonedDateTimeItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(ZonedDateTimeItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        ZonedDateTimeItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(ZonedDateTimeItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        ZonedDateTimeItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(
                        ZonedDateTimeItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(
                        ZonedDateTimeItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        ZonedDateTimeItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(ZonedDateTimeItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}

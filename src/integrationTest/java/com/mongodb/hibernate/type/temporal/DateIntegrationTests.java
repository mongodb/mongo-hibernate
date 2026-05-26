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
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.DateItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class DateIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(DateItems.WithId.class),
                () -> assertNotSupported(DateItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(DateItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(DateItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(DateItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(DateItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(DateItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        DateItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(DateItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}

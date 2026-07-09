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
import static com.mongodb.hibernate.type.temporal.UnsupportedItems.SqlTimeItems;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SqlTimeIntegrationTests {

    @Test
    void unsupported() {
        assertAll(
                () -> assertNotSupported(SqlTimeItems.WithId.class),
                () -> assertNotSupported(SqlTimeItems.WithFlattenedEmbeddableId.class),
                () -> assertNotSupported(SqlTimeItems.WithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithCollectionPersistentAttribute.class),

                // Flattened Embeddable
                () -> assertNotSupported(SqlTimeItems.WithEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithEmbeddableWithCollectionPersistentAttribute.class),

                // Nested flattened embeddable
                () -> assertNotSupported(SqlTimeItems.WithNestedEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithNestedEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithNestedEmbeddableWithCollectionPersistentAttribute.class),

                // Aggregate embeddable
                () -> assertNotSupported(SqlTimeItems.WithAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithCollectionOfAggregateEmbeddable.class),

                // Nested aggregate embeddable
                () -> assertNotSupported(SqlTimeItems.WithNestedAggregateEmbeddableWithBasicPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithNestedAggregateEmbeddableWithArrayPersistentAttribute.class),
                () -> assertNotSupported(
                        SqlTimeItems.WithNestedAggregateEmbeddableWithCollectionPersistentAttribute.class),
                () -> assertNotSupported(SqlTimeItems.WithNestedCollectionOfAggregateEmbeddable.class));
    }
}
